package jconnect.network;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class DeviceConnection {
    // Protocol Constants
    private static final byte CMD_HEARTBEAT = 0;
    private static final byte CMD_MSG = 1;
    private static final byte CMD_FILE = 2;

    private String remoteIp;
    private Socket socket;
    private DataOutputStream out; // Changed from PrintWriter
    private DataInputStream in;   // Changed from BufferedReader
    
    private final ConnectionObserver observer;
    private volatile boolean isOnline = false;
    private long lastSeen = 0;
    
    // Thread Safety Lock
    private final Object streamLock = new Object();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // --- Constructors ---
    public DeviceConnection(String remoteIp, int port, ConnectionObserver observer) {
        this.remoteIp = remoteIp;
        this.observer = observer;
        startManager();
    }

    public DeviceConnection(Socket existingSocket, ConnectionObserver observer) {
        this.socket = existingSocket;
        this.remoteIp = existingSocket.getInetAddress().getHostAddress();
        this.observer = observer;
        try {
            setupStreams();
        } catch (IOException e) {
            handleDisconnect();
        }
        startManager();
    }

    // --- Stream Setup ---
    private void setupStreams() throws IOException {
        // Use Data Streams for mixed Binary/Text content
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        
        isOnline = true;
        lastSeen = System.currentTimeMillis();
        observer.onStatusChange(remoteIp, true);
        
        new Thread(this::listen).start();
    }

    // --- Heartbeat Manager ---
    private void startManager() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isOnline) {
                try {
                    synchronized (streamLock) {
                        out.writeByte(CMD_HEARTBEAT);
                        out.flush();
                    }
                    if (System.currentTimeMillis() - lastSeen > 10000) handleDisconnect();
                } catch (IOException e) { handleDisconnect(); }
            } else {
                attemptReconnect();
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void attemptReconnect() {
        if (socket != null && !socket.isClosed()) return;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(remoteIp, 5000), 2000);
            setupStreams();
        } catch (IOException e) { /* Retry next cycle */ }
    }

    // --- Listening Loop (The Receiver) ---
    private void listen() {
        try {
            while (isOnline) {
                // Read the Command Byte first
                byte type = in.readByte();
                lastSeen = System.currentTimeMillis();

                switch (type) {
                    case CMD_HEARTBEAT:
                        // Just updates lastSeen
                        break;
                    case CMD_MSG:
                        String text = in.readUTF();
                        observer.onMessage(remoteIp, text);
                        break;
                    case CMD_FILE:
                        receiveFile();
                        break;
                }
            }
        } catch (IOException e) {
            handleDisconnect();
        }
    }

    // --- File Reception Logic ---
    private void receiveFile() throws IOException {
        String fileName = in.readUTF();
        long fileSize = in.readLong();

        // Cross-platform path
        File downloadDir = new File(System.getProperty("user.home"), "Downloads");
        if (!downloadDir.exists()) downloadDir.mkdirs();

        File partFile = new File(downloadDir, fileName + ".part");
        File finalFile = new File(downloadDir, fileName);

        observer.onMessage(remoteIp, "Receiving file: " + fileName + " (" + (fileSize/1024) + " KB)");

        try (FileOutputStream fos = new FileOutputStream(partFile)) {
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int bytesRead;

            while (totalRead < fileSize) {
                // Calculate remaining bytes to avoid over-reading into next packet
                int remaining = (int) Math.min(buffer.length, fileSize - totalRead);
                bytesRead = in.read(buffer, 0, remaining);
                
                if (bytesRead == -1) throw new IOException("Unexpected End of Stream");
                
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                // Report Progress
                int percent = (int) ((totalRead * 100) / fileSize);
                observer.onFileProgress(remoteIp, fileName, percent);
            }
        }
        
        // Rename .part to final
        if (partFile.renameTo(finalFile)) {
            observer.onMessage(remoteIp, "File saved: " + finalFile.getAbsolutePath());
            observer.onFileProgress(remoteIp, fileName, 100);
        } else {
            observer.onMessage(remoteIp, "Error renaming file chunk.");
        }
    }

    // --- Send Methods (Thread Safe) ---

    public void sendText(String msg) {
        if (!isOnline) return;
        new Thread(() -> {
            synchronized (streamLock) {
                try {
                    out.writeByte(CMD_MSG);
                    out.writeUTF(msg);
                    out.flush();
                } catch (IOException e) { handleDisconnect(); }
            }
        }).start();
    }

    public void sendFile(File file) {
        if (!isOnline) return;
        new Thread(() -> {
            synchronized (streamLock) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    // 1. Send Header
                    out.writeByte(CMD_FILE);
                    out.writeUTF(file.getName());
                    out.writeLong(file.length());
                    
                    // 2. Send Chunks
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalSent = 0;
                    long fileSize = file.length();

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;
                        
                        // Update UI rarely to avoid UI lag, or use modulo
                        if (totalSent % (4096 * 10) == 0 || totalSent == fileSize) {
                             int percent = (int) ((totalSent * 100) / fileSize);
                             observer.onFileProgress(remoteIp, file.getName() + " (Sending)", percent);
                        }
                    }
                    out.flush();
                    observer.onMessage(remoteIp, "File sent: " + file.getName());

                } catch (IOException e) {
                    observer.onMessage(remoteIp, "Error sending file: " + e.getMessage());
                    handleDisconnect();
                }
            }
        }).start();
    }

    private void handleDisconnect() {
        if (isOnline) {
            isOnline = false;
            observer.onStatusChange(remoteIp, false);
            try { if (socket != null) socket.close(); } catch (IOException e) {}
        }
    }
}