package jconnect.network;

import java.net.*;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private static final String PROTOCOL_PREFIX = "JCONNECT_v1|";
    private DatagramSocket socket;
    private boolean running = true;

    public void start() {
        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
            new Thread(this::broadcastPresence).start();
            new Thread(this::listenForPeers).start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void broadcastPresence() {
        try {
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            byte[] buffer = (PROTOCOL_PREFIX + System.getProperty("user.name")).getBytes();
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddr, DISCOVERY_PORT);
                socket.send(packet);
                Thread.sleep(4000); 
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void listenForPeers() {
        byte[] buffer = new byte[1024];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                String senderIp = packet.getAddress().getHostAddress();

                if (msg.startsWith(PROTOCOL_PREFIX) && !senderIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                    DeviceRegistry.updateDevice(senderIp);
                }
            } catch (Exception e) { /* Closed */ }
        }
    }
}