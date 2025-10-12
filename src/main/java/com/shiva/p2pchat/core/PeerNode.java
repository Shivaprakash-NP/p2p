package com.shiva.p2pchat.core;

import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.shiva.p2pchat.crypto.CryptoUtils;
import com.shiva.p2pchat.crypto.KeyManager;
import com.shiva.p2pchat.discovery.PeerDiscovery;
import com.shiva.p2pchat.model.Message;
import com.shiva.p2pchat.ui.AnsiColors;

public class PeerNode {

    private final String username;
    private final int tcpPort;
    private final KeyManager keyManager;
    private final PeerDiscovery peerDiscovery;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    private final Map<String, List<String>> messageRequests = new ConcurrentHashMap<>();
    private final Map<String, String> activeChats = new ConcurrentHashMap<>(); // Maps user to their accepted chat partner
    private String currentChatPartner = null;

    public PeerNode(String username, int tcpPort, int discoveryPort, KeyManager keyManager) {
        this.username = username;
        this.tcpPort = tcpPort;
        this.keyManager = keyManager;
        this.peerDiscovery = new PeerDiscovery(username, tcpPort, discoveryPort, CryptoUtils.keyToString(keyManager.getPublicKey()));
    }

    public void start() {
        executorService.submit(peerDiscovery);
        startServerListener();
        handleUserInput();
    }
    
    public KeyManager getKeyManager() { return keyManager; }

    private void startServerListener() {
        executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(new ConnectionHandler(clientSocket, this));
                }
            } catch (Exception e) {
                if (running) System.err.println("Server listener failed: " + e.getMessage());
            }
        });
    }

    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            if (currentChatPartner != null) {
                handleChatInput(scanner);
            } else {
                handleCommandInput(scanner);
            }
        }
    }

    private void handleCommandInput(Scanner scanner) {
        System.out.print(AnsiColors.ANSI_CYAN + "\n(online | inbox | chat <user> <msg> | exit) > " + AnsiColors.ANSI_RESET);
        String line = scanner.nextLine();
        String[] parts = line.split("\\s+", 3);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "online": listPeers(); break;
            case "inbox": viewInbox(scanner); break;
            case "chat":
                if (parts.length == 3) sendMessageRequest(parts[1], parts[2]);
                else System.out.println("Usage: chat <username> <message>");
                break;
            case "exit": stop(); break;
            default: System.out.println("Unknown command.");
        }
    }

    private void handleChatInput(Scanner scanner) {
        System.out.print(AnsiColors.ANSI_CYAN + "> " + AnsiColors.ANSI_RESET);
        String messageText = scanner.nextLine();
        if ("/back".equalsIgnoreCase(messageText)) {
            System.out.println(AnsiColors.ANSI_YELLOW + "[SYSTEM] Exited chat with " + currentChatPartner + AnsiColors.ANSI_RESET);
            currentChatPartner = null;
            return;
        }
        sendMessage(currentChatPartner, messageText, Message.MessageType.CHAT);
    }

    private void listPeers() {
        System.out.println(AnsiColors.ANSI_YELLOW + "\n--- Online Users ---" + AnsiColors.ANSI_RESET);
        Map<String, ?> peers = peerDiscovery.getOnlinePeers();
        if (peers.isEmpty()) {
            System.out.println("No other users found.");
        } else {
            peers.keySet().forEach(System.out::println);
        }
        System.out.println(AnsiColors.ANSI_YELLOW + "--------------------" + AnsiColors.ANSI_RESET);
    }
    
    public void addMessageRequest(String fromUser, String message) {
        messageRequests.computeIfAbsent(fromUser, k -> new ArrayList<>()).add(message);
    }

    private void viewInbox(Scanner scanner) {
        System.out.println(AnsiColors.ANSI_YELLOW + "\n--- Message Requests (" + messageRequests.size() + ") ---" + AnsiColors.ANSI_RESET);
        messageRequests.keySet().forEach(user -> System.out.println("- " + user));
        System.out.println(AnsiColors.ANSI_YELLOW + "------------------------------" + AnsiColors.ANSI_RESET);

        if (!messageRequests.isEmpty()) {
            System.out.print(AnsiColors.ANSI_CYAN + "(accept <user> | read <user> | back) > " + AnsiColors.ANSI_RESET);
            String line = scanner.nextLine();
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                if ("accept".equalsIgnoreCase(parts[0])) {
                    acceptChatRequest(parts[1]);
                } else if ("read".equalsIgnoreCase(parts[0])) {
                    readMessagesFrom(parts[1]);
                }
            }
        }
    }

    private void readMessagesFrom(String user) {
        List<String> messages = messageRequests.get(user);
        if (messages != null) {
            System.out.println(AnsiColors.ANSI_YELLOW + "\n--- Messages from " + user + " ---" + AnsiColors.ANSI_RESET);
            messages.forEach(System.out::println);
        } else {
            System.out.println("No messages from that user.");
        }
    }

    private void acceptChatRequest(String targetUsername) {
        if (!messageRequests.containsKey(targetUsername)) {
            System.out.println("No request from that user.");
            return;
        }
        List<String> messages = messageRequests.remove(targetUsername);
        activeChats.put(username, targetUsername);
        currentChatPartner = targetUsername;
        
        sendMessage(targetUsername, "ACCEPT", Message.MessageType.ACCEPT_REQUEST);
        
        System.out.println("--- Chat with " + targetUsername + " ---");
        messages.forEach(msg -> System.out.println(AnsiColors.ANSI_GREEN + "[" + targetUsername + "]: " + msg + AnsiColors.ANSI_RESET));
    }
    
    public void startChatSession(String partner) {
        currentChatPartner = partner;
    }
    
    public boolean isInChatWith(String partner) {
        return partner.equals(currentChatPartner);
    }

    private void sendMessageRequest(String targetUsername, String message) {
        if (username.equals(targetUsername)) {
            System.out.println("You can't chat with yourself.");
            return;
        }
        sendMessage(targetUsername, message, Message.MessageType.REQUEST);
        System.out.println(AnsiColors.ANSI_YELLOW + "[SYSTEM] Message request sent to '" + targetUsername + "'." + AnsiColors.ANSI_RESET);
    }

    private void sendMessage(String targetUsername, String message, Message.MessageType type) {
        PeerDiscovery.DiscoveredPeer peer = peerDiscovery.getPeer(targetUsername);
        if (peer == null) {
            System.out.println("User '" + targetUsername + "' is not online.");
            return;
        }

        try (Socket socket = new Socket(peer.ip, peer.port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            
            byte[] encryptedContent = CryptoUtils.encrypt(message, peer.publicKey);
            Message msgObject = new Message(type, username, encryptedContent);
            out.writeObject(msgObject);
            out.flush();

        } catch (Exception e) {
            System.err.println("Error sending message to " + targetUsername + ": " + e.getMessage());
        }
    }

    public void stop() {
        System.out.println("Shutting down...");
        this.running = false;
        peerDiscovery.stop();
        executorService.shutdownNow();
        System.exit(0);
    }
}