package jconnect.network;

import java.io.*;
import java.net.*;

public class FileServer {
    public void startServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);
            
            // This line stops the program and waits for the partner to connect
            Socket socket = serverSocket.accept(); 
            System.out.println("Client connected!");

            // Setup reading
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            System.out.println("Received from partner: " + message);
            
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}