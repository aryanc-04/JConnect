package jconnect.network;

import java.io.*;
import java.net.*;

public class SimpleServer {
    public static void main(String[] args) {
        int port = 5000;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Waiting for client on port " + port + "...");
            
            // Execution stops here until a client connects
            Socket socket = serverSocket.accept(); 
            System.out.println("Client connected from: " + socket.getInetAddress());

            // Get the input stream to read what the client sent
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            String message = reader.readLine();
            System.out.println("Message received: " + message);

            socket.close();
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
        }
    }
}