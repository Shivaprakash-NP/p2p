package com.shiva.p2pchat.core;

import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.shiva.p2pchat.crypto.CryptoUtils;
import com.shiva.p2pchat.crypto.KeyManager;
import com.shiva.p2pchat.discovery.PeerDiscovery;
import com.shiva.p2pchat.model.Message;

public class PeerNode {

    private final String username;
    private final int tcpPort;
    private final KeyManager keyManager;
    private final PeerDiscovery peerDiscovery;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private boolean running = true;

    public PeerNode(String username, int tcpPort, int discoveryPort, KeyManager keyManager) {
        this.username = username;
        this.tcpPort = tcpPort;
        this.keyManager = keyManager;
        this.peerDiscovery = new PeerDiscovery(username, tcpPort, discoveryPort);
    }

    public void start() {
        executorService.submit(peerDiscovery);

        try {
            serverSocket = new ServerSocket(tcpPort);
            System.out.println("Listening for chats on port " + tcpPort);
            
            Thread listenerThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executorService.submit(new ConnectionHandler(clientSocket, username, keyManager.getPrivateKey()));
                    } catch (Exception e) {
                        if (running) System.err.println("Server listener error: " + e.getMessage());
                    }
                }
            });
            listenerThread.start();
        } catch (Exception e) {
            System.err.println("Could not start TCP server on port " + tcpPort + ": " + e.getMessage());
            return;
        }

        handleUserInput();
    }
    
    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.print("\nEnter command (list, chat <user>, mykey, exit): ");
            String line = scanner.nextLine();
            String[] parts = line.split("\\s+", 2);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "list":
                    listPeers();
                    break;
                case "chat":
                    if (parts.length == 2) {
                        initiateChat(parts[1]);
                    } else {
                        System.out.println("Usage: chat <username>");
                    }
                    break;
                case "mykey":
                    System.out.println("\nYour Public Key (Share this with others):");
                    System.out.println(CryptoUtils.keyToString(keyManager.getPublicKey()));
                    break;
                case "exit":
                    stop();
                    break;
                default:
                    System.out.println("Unknown command.");
            }
        }
        scanner.close();
    }
    
    private void listPeers() {
        System.out.println("\n--- Online Users ---");
        Map<String, ?> peers = peerDiscovery.getOnlinePeers();
        if (peers.isEmpty()) {
            System.out.println("No other users found on the network.");
        } else {
            peers.keySet().forEach(System.out::println);
        }
        System.out.println("--------------------");
    }

    private void initiateChat(String targetUsername) {
        String ip = peerDiscovery.getPeerIp(targetUsername);
        int port = peerDiscovery.getPeerPort(targetUsername);

        if (ip == null) {
            System.out.println("User '" + targetUsername + "' not found or is offline.");
            return;
        }

        System.out.println("Please paste the public key for '" + targetUsername + "':");
        Scanner scanner = new Scanner(System.in);
        String pubKeyStr = scanner.nextLine();
        PublicKey targetPublicKey;
        try {
            targetPublicKey = CryptoUtils.stringToPublicKey(pubKeyStr);
        } catch (Exception e) {
            System.out.println("Invalid public key format.");
            return;
        }

        try (Socket socket = new Socket(ip, port)) {
            System.out.println("Secure connection established with " + targetUsername + ". Type '/exit' to end.");

            Thread listener = new Thread(new ConnectionHandler(socket, username, keyManager.getPrivateKey()));
            listener.start();
            
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            while (true) {
                System.out.print("> ");
                String messageText = scanner.nextLine();
                if ("/exit".equalsIgnoreCase(messageText)) {
                    break;
                }

                byte[] encryptedMessage = CryptoUtils.encrypt(messageText, targetPublicKey);
                Message message = new Message(username, encryptedMessage);
                out.writeObject(message);
                out.flush();
            }

        } catch (Exception e) {
            System.err.println("Failed to connect or chat with " + targetUsername + ": " + e.getMessage());
        }
    }
    
    public void stop() {
        System.out.println("Shutting down...");
        this.running = false;
        peerDiscovery.stop();
        executorService.shutdownNow();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) { /* ignore */ }
        System.exit(0);
    }
}