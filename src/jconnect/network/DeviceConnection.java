package jconnect.network;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class DeviceConnection {
    private static final byte CMD_HEARTBEAT = 0;
    private static final byte CMD_MSG = 1;
    private static final byte CMD_FILE = 2;
    private static final byte CMD_ACK = 3; 

    private String remoteIp;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    
    private final ConnectionObserver observer;
    private volatile boolean isOnline = false;
    private volatile long lastSeen = 0; 
    
    private final Object writeLock = new Object(); 
    
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
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        
        isOnline = true;
        lastSeen = System.currentTimeMillis();
        observer.onStatusChange(remoteIp, true);
        
        new Thread(this::listen).start();
    }

    private void startManager() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isOnline) {
                try {
                    synchronized (writeLock) {
                        out.writeByte(CMD_HEARTBEAT);
                        out.flush();
                    }
                    
                    if (System.currentTimeMillis() - lastSeen > 20000) {
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
        } catch (IOException e) { }
    }

    private void listen() {
        try {
            while (isOnline) {
                byte type = in.readByte();
                
                lastSeen = System.currentTimeMillis();

                switch (type) {
                    case CMD_HEARTBEAT: 
                        break;
                    case CMD_ACK:
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

    private void receiveFile() throws IOException {
        String fileName = in.readUTF();
        long fileSize = in.readLong();
        
        File downloadDir = new File(System.getProperty("user.home"), "Downloads");
        if (!downloadDir.exists()) downloadDir.mkdirs();
        
        File partFile = new File(downloadDir, fileName + ".part");
        File finalFile = new File(downloadDir, fileName);

        observer.onMessage(remoteIp, "Receiving: " + fileName);

        try (FileOutputStream fos = new FileOutputStream(partFile)) {
            byte[] buffer = new byte[8192]; 
            long totalRead = 0;
            int bytesRead;
            long lastAckTime = 0;

            while (totalRead < fileSize) {
                int remaining = (int) Math.min(buffer.length, fileSize - totalRead);
                bytesRead = in.read(buffer, 0, remaining);
                
                if (bytesRead == -1) throw new IOException("Premature End of Stream");

                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                lastSeen = System.currentTimeMillis();

                if (System.currentTimeMillis() - lastAckTime > 1000) {
                    synchronized (writeLock) {
                        out.writeByte(CMD_ACK);
                        out.flush();
                    }
                    lastAckTime = System.currentTimeMillis();
                    
                    int percent = (int) ((totalRead * 100) / fileSize);
                    observer.onFileProgress(remoteIp, fileName, percent);
                }
            }
            fos.flush(); 
        }

        if (finalFile.exists()) finalFile.delete(); 
        if (partFile.renameTo(finalFile)) {
            observer.onMessage(remoteIp, "Saved: " + finalFile.getName());
            observer.onFileProgress(remoteIp, fileName, 100);
        } else {
            observer.onMessage(remoteIp, "Error: Could not rename .part file");
        }
    }

    public void sendFile(File file) {
        if (!isOnline) return;
        new Thread(() -> {
            synchronized (writeLock) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    out.writeByte(CMD_FILE);
                    out.writeUTF(file.getName());
                    out.writeLong(file.length());
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalSent = 0;
                    long fileSize = file.length();
                    long lastUiUpdate = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;
                        
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