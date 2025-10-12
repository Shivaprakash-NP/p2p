package com.shiva.p2pchat.core;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.PrivateKey;

import com.shiva.p2pchat.crypto.CryptoUtils;
import com.shiva.p2pchat.model.Message;

public class ConnectionHandler implements Runnable {

    private final Socket socket;
    private final PrivateKey privateKey;
    private final String localUsername;
    private String remoteUsername;

    public ConnectionHandler(Socket socket, String localUsername, PrivateKey privateKey) {
        this.socket = socket;
        this.localUsername = localUsername;
        this.privateKey = privateKey;
    }

    @Override
    public void run() {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            // Listen for incoming messages in a separate thread
            Thread listenerThread = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        Message receivedMsg = (Message) in.readObject();
                        this.remoteUsername = receivedMsg.getSenderUsername();
                        String decryptedContent = CryptoUtils.decrypt(receivedMsg.getEncryptedContent(), privateKey);
                        System.out.println("\n[" + remoteUsername + "]: " + decryptedContent);
                        System.out.print("> "); // Prompt for next message
                    }
                } catch (Exception e) {
                     System.out.println("\nConnection with " + (remoteUsername != null ? remoteUsername : "peer") + " closed.");
                }
            });
            listenerThread.start();
            
            listenerThread.join(); // Wait for listener to finish

        } catch (Exception e) {
            // Error is handled in the listener thread
        } finally {
            try {
                socket.close();
            } catch (Exception e) { /* ignore */ }
        }
    }
}