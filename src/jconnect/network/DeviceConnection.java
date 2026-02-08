package jconnect.network;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class DeviceConnection {
    private String remoteIp;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ConnectionObserver observer;
    
    private volatile boolean isOnline = false;
    private long lastSeen = 0;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Initial connection attempt
    public DeviceConnection(String remoteIp, int port, ConnectionObserver observer) {
        this.remoteIp = remoteIp;
        this.port = port;
        this.observer = observer;
        startManager();
    }

    // Handover for incoming connections
    public DeviceConnection(Socket existingSocket, ConnectionObserver observer) {
        this.socket = existingSocket;
        this.remoteIp = existingSocket.getInetAddress().getHostAddress();
        this.port = existingSocket.getPort();
        this.observer = observer;
        setupStreams();
        startManager();
    }

    private synchronized void setupStreams() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isOnline = true;
            lastSeen = System.currentTimeMillis();
            observer.onStatusChange(remoteIp, true);
            
            // Start the "Ears" thread
            new Thread(this::listen).start();
        } catch (IOException e) {
            isOnline = false;
        }
    }

    private void startManager() {
        // Run every 5 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (isOnline) {
                out.println("HB:PING");
                if (out.checkError() || (System.currentTimeMillis() - lastSeen > 10000)) {
                    handleDisconnect();
                }
            } else {
                attemptReconnect();
            }
            // Continuous Status Reporting
            System.out.println("[MONITOR] " + remoteIp + " is " + (isOnline ? "ONLINE" : "OFFLINE (Retrying...)"));
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void attemptReconnect() {
        try {
            // Try to connect with a 2-second timeout so we don't hang the scheduler
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(remoteIp, 5000), 2000);
            setupStreams();
        } catch (IOException e) {
            // Silently fail; manager will call this again in 5 seconds
        }
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                lastSeen = System.currentTimeMillis();
                if (line.equals("HB:PING")) continue;
                if (line.startsWith("MSG:")) {
                    observer.onMessage(remoteIp, line.substring(4));
                }
            }
        } catch (IOException e) {
            handleDisconnect();
        }
    }

    private void handleDisconnect() {
        if (isOnline) {
            isOnline = false;
            observer.onStatusChange(remoteIp, false);
            try { if (socket != null) socket.close(); } catch (IOException e) {}
        }
    }

    public void send(String msg) {
        if (isOnline && out != null) {
            out.println("MSG:" + msg);
        }
    }
}