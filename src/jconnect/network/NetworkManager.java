package jconnect.network;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class NetworkManager implements ConnectionObserver {
    private final Map<String, DeviceConnection> activeConnections = new ConcurrentHashMap<>();
    private final ConnectionObserver uiObserver;
    private final DiscoveryService discoveryService;

    public NetworkManager(ConnectionObserver uiObserver) {
        this.uiObserver = uiObserver;
        this.discoveryService = new DiscoveryService();
    }

    public void start() {
        discoveryService.start();
        startServer();
    }

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(5000)) {
                while (true) {
                    Socket s = ss.accept();
                    String ip = s.getInetAddress().getHostAddress();
                    // Only create if we don't already have a valid connection
                    DeviceConnection dc = new DeviceConnection(s, this);
                    activeConnections.put(ip, dc);
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    public void sendMessageTo(String ip, String message) {
        DeviceConnection dc = activeConnections.get(ip);
        if (dc == null) {
            // If no connection exists, try to initiate one
            dc = new DeviceConnection(ip, 5000, this);
            activeConnections.put(ip, dc);
        }
        dc.send(message);
    }

    @Override
    public void onMessage(String ip, String message) {
        uiObserver.onMessage(ip, message);
    }

    @Override
    public void onStatusChange(String ip, boolean isOnline) {
        if (!isOnline) activeConnections.remove(ip);
        uiObserver.onStatusChange(ip, isOnline);
    }
}