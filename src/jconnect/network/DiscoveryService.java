package jconnect.network;

import java.net.*;
import java.util.concurrent.*;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private static final String DISCOVERY_MSG = "SHARE_APP_DISCOVERY";
    private DatagramSocket socket;
    private boolean running = true;

    public void start() {
        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);

            // Thread 1: Broadcast our existence every 5 seconds
            new Thread(this::broadcastPresence).start();

            // Thread 2: Listen for others
            new Thread(this::listenForPeers).start();
            
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void broadcastPresence() {
        try {
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            byte[] buffer = DISCOVERY_MSG.getBytes();

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddr, DISCOVERY_PORT);
                socket.send(packet);
                Thread.sleep(5000);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void listenForPeers() {
        byte[] buffer = new byte[1024];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Blocks until a shout is heard
                
                String msg = new String(packet.getData(), 0, packet.getLength());
                String senderIp = packet.getAddress().getHostAddress();
                
                // Don't discover yourself
                if (msg.equals(DISCOVERY_MSG) && !senderIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                    DeviceRegistry.updateDevice(senderIp);
                }
            } catch (Exception e) { /* Socket closed */ }
        }
    }
}