package jconnect.network;

import java.io.*;
import java.net.*;

public class SimpleClient {
    public static void main(String[] args) {
        String hostname = "10.164.249.52"; // PUT SERVER IP HERE
        int port = 5000;

        try (Socket socket = new Socket(hostname, port)) {
            // Get the output stream to send data to the server
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            writer.println("Hello from the other side!");
            System.out.println("Message sent!");

        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }
}