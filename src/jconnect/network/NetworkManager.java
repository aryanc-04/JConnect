package jconnect.network;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class NetworkManager implements ConnectionObserver {
    private final Map<String, DeviceConnection> activeConnections = new ConcurrentHashMap<>();
    private final ConnectionObserver uiObserver;
    private final DiscoveryService discoveryService;
    private String myIp;

    public NetworkManager(ConnectionObserver uiObserver) {
        this.uiObserver = uiObserver;
        this.discoveryService = new DiscoveryService();
        try {
            this.myIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) { this.myIp = "127.0.0.1"; }
    }

    public void start() {
        discoveryService.start();
        new Thread(this::startServer).start();
    }

    private void startServer() {
        try (ServerSocket ss = new ServerSocket(5000)) {
            while (true) {
                Socket s = ss.accept();
                String partnerIp = s.getInetAddress().getHostAddress();
                
                if (!activeConnections.containsKey(partnerIp)) {
                    activeConnections.put(partnerIp, new DeviceConnection(s, this));
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // NEW: Explicitly connect when UI selects a device
    public void connectTo(String targetIp) {
        if (targetIp == null || targetIp.equals(myIp)) return;
        
        // Don't create if exists and is online
        if (activeConnections.containsKey(targetIp)) {
            DeviceConnection conn = activeConnections.get(targetIp);
            if(conn.isConnected()) {
                uiObserver.onStatusChange(targetIp, true);
                return;
            }
        }

        new Thread(() -> {
            DeviceConnection dc = new DeviceConnection(targetIp, 5000, this);
            activeConnections.put(targetIp, dc);
        }).start();
    }

    // NEW: Explicitly disconnect
    public void disconnectFrom(String targetIp) {
        if (targetIp == null) return;
        DeviceConnection conn = activeConnections.remove(targetIp);
        if (conn != null) {
            conn.shutdown();
        }
    }

    public void sendMessageTo(String targetIp, String message) {
        DeviceConnection conn = activeConnections.get(targetIp);
        if (conn != null) conn.sendText(message);
    }
    
    public void sendFileTo(String targetIp, File file) {
        DeviceConnection conn = activeConnections.get(targetIp);
        if (conn != null) conn.sendFile(file);
    }

    @Override
    public void onMessage(String ip, String msg) { uiObserver.onMessage(ip, msg); }

    @Override
    public void onStatusChange(String ip, boolean online) {
        if (!online) activeConnections.remove(ip);
        uiObserver.onStatusChange(ip, online);
    }

    @Override
    public void onFileProgress(String ip, String file, int percent) {
        uiObserver.onFileProgress(ip, file, percent);
    }
}