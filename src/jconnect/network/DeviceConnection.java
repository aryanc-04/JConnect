package jconnect.network;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class DeviceConnection {
    // --- Protocol Constants ---
    private static final byte CMD_HEARTBEAT = 0;
    private static final byte CMD_MSG = 1;
    private static final byte CMD_FILE = 2;
    private static final byte CMD_ACK = 3; // New: Keeps sender alive during transfers

    // --- State ---
    private String remoteIp;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    
    private final ConnectionObserver observer;
    private volatile boolean isOnline = false;
    private volatile long lastSeen = 0; // volatile is crucial for thread visibility
    
    // Locks
    private final Object writeLock = new Object(); // Only locks WRITING, not reading
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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

    private void setupStreams() throws IOException {
        // Buffered streams are essential for performance
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        
        isOnline = true;
        lastSeen = System.currentTimeMillis();
        observer.onStatusChange(remoteIp, true);
        
        new Thread(this::listen).start();
    }

    private void startManager() {
        // Watchdog: Checks connection health every 2 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (isOnline) {
                try {
                    // 1. Send Heartbeat (only if not busy sending a huge file)
                    // We use tryLock to avoid blocking if a file is uploading
                    synchronized (writeLock) {
                        out.writeByte(CMD_HEARTBEAT);
                        out.flush();
                    }
                    
                    // 2. Check if partner is dead
                    // Timeout increased to 20s for stability
                    if (System.currentTimeMillis() - lastSeen > 20000) {
                        System.err.println("Timeout: No data from " + remoteIp + " for 20s");
                        handleDisconnect();
                    }
                } catch (IOException e) { handleDisconnect(); }
            } else {
                attemptReconnect();
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void attemptReconnect() {
        if (socket != null && !socket.isClosed()) return;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(remoteIp, 5000), 2000);
            setupStreams();
        } catch (IOException e) { /* Quiet fail, retry next tick */ }
    }

    // --- The Reader Thread ---
    private void listen() {
        try {
            while (isOnline) {
                // Blocking read - waits for next command
                byte type = in.readByte();
                
                // IMPORTANT: Any data received means they are alive
                lastSeen = System.currentTimeMillis();

                switch (type) {
                    case CMD_HEARTBEAT: 
                        // lastSeen already updated above
                        break;
                    case CMD_ACK:
                        // Acknowledgement received (usually during file transfer)
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

    // --- Robust File Receiver ---
    private void receiveFile() throws IOException {
        String fileName = in.readUTF();
        long fileSize = in.readLong();
        
        File downloadDir = new File(System.getProperty("user.home"), "Downloads");
        if (!downloadDir.exists()) downloadDir.mkdirs();
        
        File partFile = new File(downloadDir, fileName + ".part");
        File finalFile = new File(downloadDir, fileName);

        observer.onMessage(remoteIp, "Receiving: " + fileName);

        try (FileOutputStream fos = new FileOutputStream(partFile)) {
            byte[] buffer = new byte[8192]; // 8KB buffer
            long totalRead = 0;
            int bytesRead;
            long lastAckTime = 0;

            while (totalRead < fileSize) {
                // Calculate bytes remaining
                int remaining = (int) Math.min(buffer.length, fileSize - totalRead);
                bytesRead = in.read(buffer, 0, remaining);
                
                if (bytesRead == -1) throw new IOException("Premature End of Stream");

                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                // CRITICAL FIX 1: Update lastSeen so WE don't timeout
                lastSeen = System.currentTimeMillis();

                // CRITICAL FIX 2: Send ACK back every ~1 second so SENDER doesn't timeout
                if (System.currentTimeMillis() - lastAckTime > 1000) {
                    synchronized (writeLock) {
                        out.writeByte(CMD_ACK);
                        out.flush();
                    }
                    lastAckTime = System.currentTimeMillis();
                    
                    // Update UI
                    int percent = (int) ((totalRead * 100) / fileSize);
                    observer.onFileProgress(remoteIp, fileName, percent);
                }
            }
            fos.flush(); // Ensure all data is on disk
        }

        // Rename logic
        if (finalFile.exists()) finalFile.delete(); // Overwrite if exists
        if (partFile.renameTo(finalFile)) {
            observer.onMessage(remoteIp, "Saved: " + finalFile.getName());
            observer.onFileProgress(remoteIp, fileName, 100);
        } else {
            observer.onMessage(remoteIp, "Error: Could not rename .part file");
        }
    }

    // --- Robust File Sender ---
    public void sendFile(File file) {
        if (!isOnline) return;
        new Thread(() -> {
            // We lock the stream for writing. 
            // Note: The 'listen' thread can still READ (receiving ACKs) while we hold this lock.
            synchronized (writeLock) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    // 1. Header
                    out.writeByte(CMD_FILE);
                    out.writeUTF(file.getName());
                    out.writeLong(file.length());
                    
                    // 2. Data
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalSent = 0;
                    long fileSize = file.length();
                    long lastUiUpdate = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;
                        
                        // Update UI every 500ms (prevent UI flooding)
                        if (System.currentTimeMillis() - lastUiUpdate > 500) {
                            int percent = (int) ((totalSent * 100) / fileSize);
                            observer.onFileProgress(remoteIp, file.getName(), percent);
                            lastUiUpdate = System.currentTimeMillis();
                        }
                    }
                    out.flush();
                    observer.onMessage(remoteIp, "Sent: " + file.getName());
                    observer.onFileProgress(remoteIp, file.getName(), 100);

                } catch (IOException e) {
                    observer.onMessage(remoteIp, "Upload Failed: " + e.getMessage());
                    handleDisconnect();
                }
            }
        }).start();
    }

    public void sendText(String msg) {
        if (!isOnline) return;
        new Thread(() -> {
            synchronized (writeLock) {
                try {
                    out.writeByte(CMD_MSG);
                    out.writeUTF(msg);
                    out.flush();
                } catch (IOException e) { handleDisconnect(); }
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