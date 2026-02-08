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

    public void sendMessageTo(String targetIp, String message) {
        DeviceConnection conn = getConnection(targetIp);
        if (conn != null) conn.sendText(message);
    }
    
    public void sendFileTo(String targetIp, File file) {
        DeviceConnection conn = getConnection(targetIp);
        if (conn != null) conn.sendFile(file);
    }

    private DeviceConnection getConnection(String targetIp) {
        if (!activeConnections.containsKey(targetIp)) {
            if (myIp.compareTo(targetIp) > 0) {
                DeviceConnection dc = new DeviceConnection(targetIp, 5000, this);
                activeConnections.put(targetIp, dc);
                return dc;
            } else {
                uiObserver.onMessage("SYSTEM", "Waiting for " + targetIp + " to connect (Priority Rule)...");
                return null;
            }
        }
        return activeConnections.get(targetIp);
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