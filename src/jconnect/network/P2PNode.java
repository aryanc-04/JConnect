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
        // 1. Thread to listen for incoming connections
        new Thread(this::acceptConnections).start();

        // 2. Simple Console UI
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n1. Add Device (IP) | 2. Send Message | 3. Exit");
            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 1) {
                System.out.print("Enter IP: ");
                String ip = scanner.nextLine();
                connections.add(new DeviceConnection(ip, 5000, this));
            } else if (choice == 2) {
                System.out.print("Message: ");
                String msg = scanner.nextLine();
                // Send to all online devices
                for (DeviceConnection dc : connections) dc.send(msg);
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