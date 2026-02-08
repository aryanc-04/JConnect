package jconnect.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Test {
    private static final int UDP_PORT = 8888;
    private static final int TCP_PORT = 5000;
    private static final String DISCOVERY_MSG = "IAM_HERE";
    
    // Tracks discovered IPs and the last time they "shouted"
    private final Map<String, Long> discoveredPeers = new ConcurrentHashMap<>();
    private final Map<String, PrintWriter> activeOutStreams = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new Test().start();
    }

    public void start() {
        System.out.println("=== P2P DISCOVERY & CHAT TERMINAL ===");
        
        // 1. Start Broadcasting our existence (UDP)
        startBroadcaster();

        // 2. Listen for other broadcasters (UDP)
        startDiscoveryListener();

        // 3. Listen for incoming chat connections (TCP)
        startTcpServer();

        // 4. Console Interface
        runConsoleLoop();
    }

    private void startBroadcaster() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                byte[] buffer = DISCOVERY_MSG.getBytes();
                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddr, UDP_PORT);
                    socket.send(packet);
                    Thread.sleep(3000); // Shout every 3 seconds
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void startDiscoveryListener() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    String peerIp = packet.getAddress().getHostAddress();

                    if (msg.equals(DISCOVERY_MSG) && !peerIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                        discoveredPeers.put(peerIp, System.currentTimeMillis());
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void startTcpServer() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(TCP_PORT)) {
                while (true) {
                    Socket s = ss.accept();
                    handleNewConnection(s);
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void handleNewConnection(Socket s) {
        String ip = s.getInetAddress().getHostAddress();
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                // Register output stream for sending
                activeOutStreams.put(ip, new PrintWriter(s.getOutputStream(), true));
                
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("\n[" + ip + "]: " + line);
                    System.out.print("> ");
                }
            } catch (IOException e) {
                System.out.println("\n[SYSTEM] Lost connection to " + ip);
            } finally {
                activeOutStreams.remove(ip);
            }
        }).start();
    }

    private void runConsoleLoop() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- ONLINE PEERS ---");
            long now = System.currentTimeMillis();
            List<String> peers = new ArrayList<>();
            
            discoveredPeers.forEach((ip, time) -> {
                if (now - time < 10000) peers.add(ip); // Peer is "Online" if seen in last 10s
            });

            if (peers.isEmpty()) System.out.println("(No one found yet...)");
            for (int i = 0; i < peers.size(); i++) System.out.println((i+1) + ". " + peers.get(i));

            System.out.println("Commands: [index] [message] (e.g., '1 Hello!') | 0 to Refresh");
            System.out.print("> ");
            
            String input = scanner.nextLine();
            if (input.equals("0")) continue;

            try {
                String[] parts = input.split(" ", 2);
                int index = Integer.parseInt(parts[0]) - 1;
                String msg = parts[1];
                String targetIp = peers.get(index);

                // If not connected yet, connect
                if (!activeOutStreams.containsKey(targetIp)) {
                    Socket s = new Socket(targetIp, TCP_PORT);
                    handleNewConnection(s);
                }
                activeOutStreams.get(targetIp).println(msg);
            } catch (Exception e) {
                System.out.println("Invalid input or connection failed.");
            }
        }
    }
}