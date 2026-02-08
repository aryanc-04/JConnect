package jconnect.network;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class P2PNode implements ConnectionObserver {
    private List<DeviceConnection> connections = new ArrayList<>();

    public static void main(String[] args) {
        new P2PNode().start();
    }

    public void start() {
        // Start Discovery
        new DiscoveryService().start();
        new Thread(this::acceptConnections).start();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            List<String> online = DeviceRegistry.getOnlineDevices();
            System.out.println("\n--- DISCOVERED DEVICES ---");
            for (int i = 0; i < online.size(); i++) {
                System.out.println((i + 1) + ". " + online.get(i));
            }
            System.out.println("0. Refresh | 9. Send to All");

            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice > 0 && choice <= online.size()) {
                String targetIp = online.get(choice - 1);
                // Here you would find or create a DeviceConnection for targetIp
                System.out.println("Connecting to " + targetIp + "...");
                // Logic to send message specifically to this IP
            }
        }
    }

    private void acceptConnections() {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            while (true) {
                Socket client = serverSocket.accept();
                connections.add(new DeviceConnection(client, this));
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void onMessage(String ip, String msg) {
        System.out.println("\n[" + ip + "]: " + msg);
    }

    @Override
    public void onStatusChange(String ip, boolean online) {
        System.out.println("\n[ALERT] " + ip + " is now " + (online ? "ONLINE" : "OFFLINE"));
    }
}