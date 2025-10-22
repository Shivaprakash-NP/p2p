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
import com.shiva.p2pchat.ui.UI;

public class PeerNode {

    // --- State Management ---
    private enum AppState {
        MAIN_MENU,
        IN_CHAT,
        INBOX_VIEW
    }
    private volatile AppState currentState = AppState.MAIN_MENU;
    private volatile String currentChatPartner = null;
    private final Object printLock = new Object(); // To synchronize console output

    // --- Core Components ---
    private final String username;
    private final int tcpPort;
    private final KeyManager keyManager;
    private final PeerDiscovery peerDiscovery;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    // --- Data Stores ---
    private final Map<String, List<String>> messageRequests = new ConcurrentHashMap<>();

    public PeerNode(String username, int tcpPort, int discoveryPort, KeyManager keyManager) {
        this.username = username;
        this.tcpPort = tcpPort;
        this.keyManager = keyManager;
        this.peerDiscovery = new PeerDiscovery(username, tcpPort, discoveryPort, CryptoUtils.keyToString(keyManager.getPublicKey()));
    }

    public KeyManager getKeyManager() { return keyManager; }

    /**
     * Starts the main application loops:
     * 1. Peer Discovery (UDP)
     * 2. Server Listener (TCP)
     * 3. User Input (Console)
     */
    public void start() {
        executorService.submit(peerDiscovery);
        startServerListener();
        handleUserInput(); // This will block and run the main UI loop
    }

    private void startServerListener() {
        executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(new ConnectionHandler(clientSocket, this));
                }
            } catch (Exception e) {
                if (running) UI.printError("Server listener failed: " + e.getMessage());
            }
        });
    }

    // --- MAIN UI STATE MACHINE ---

    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            // The state determines which prompt and logic to use
            switch (currentState) {
                case MAIN_MENU:
                    handleMainMenuInput(scanner);
                    break;
                case INBOX_VIEW:
                    handleInboxInput(scanner);
                    break;
                case IN_CHAT:
                    handleChatInput(scanner);
                    break;
            }
        }
    }

    /**
     * Logic for the MAIN_MENU state.
     * Handles 'online', 'requests', 'chat', and 'exit'.
     */
    private void handleMainMenuInput(Scanner scanner) {
        System.out.print(UI.MAIN_PROMPT);
        String line = scanner.nextLine();
        String[] parts = line.split("\\s+", 3); // "chat user msg"
        String command = parts[0].toLowerCase();

        synchronized (printLock) {
            switch (command) {
                case "online":
                    listPeers();
                    break;
                case "requests":
                    listRequests();
                    currentState = AppState.INBOX_VIEW; // Change state
                    break;
                case "chat":
                    if (parts.length == 3) {
                        sendMessageRequest(parts[1], parts[2]);
                    } else {
                        UI.printError("Usage: chat <username> <message>");
                    }
                    break;
                case "exit":
                    stop();
                    break;
                default:
                    UI.printError("Unknown command.");
            }
        }
    }

    /**
     * Logic for the INBOX_VIEW state.
     * Handles 'accept', 'read', and 'back'.
     */
    private void handleInboxInput(Scanner scanner) {
        System.out.print(UI.INBOX_PROMPT);
        String line = scanner.nextLine();
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase();

        synchronized (printLock) {
            if ("back".equals(command)) {
                currentState = AppState.MAIN_MENU;
                return;
            }
            
            if (parts.length == 2) {
                String user = parts[1];
                if ("accept".equals(command)) {
                    acceptChatRequest(user);
                } else if ("read".equals(command)) {
                    readMessagesFrom(user);
                } else {
                    UI.printError("Unknown command.");
                }
            } else {
                UI.printError("Usage: <command> <username> or 'back'");
            }
        }
    }

    /**
     * Logic for the IN_CHAT state.
     * Handles sending messages and the 'quit' command.
     */
    private void handleChatInput(Scanner scanner) {
        System.out.print(UI.CHAT_PROMPT);
        String messageText = scanner.nextLine();

        synchronized (printLock) {
            if ("quit".equalsIgnoreCase(messageText)) {
                UI.printSystem("You have left the chat.");
                currentChatPartner = null;
                currentState = AppState.MAIN_MENU;
                return;
            }
            sendChatMessage(currentChatPartner, messageText);
        }
    }

    // --- COMMAND ACTIONS ---

    private void listPeers() {
        UI.printHeader("Online Users");
        Map<String, ?> peers = peerDiscovery.getOnlinePeers();
        if (peers.isEmpty()) {
            System.out.println("No other users found.");
        } else {
            peers.keySet().forEach(user -> System.out.println(UI.GREEN + "- " + user + UI.RESET));
        }
    }

    private void listRequests() {
        UI.printHeader("Message Requests (" + messageRequests.size() + ")");
        if (messageRequests.isEmpty()) {
            System.out.println("Your inbox is empty.");
        } else {
            messageRequests.keySet().forEach(user -> 
                System.out.println(UI.YELLOW + "- " + user + " (" + messageRequests.get(user).size() + " new)" + UI.RESET)
            );
        }
    }

    private void readMessagesFrom(String user) {
        List<String> messages = messageRequests.get(user);
        if (messages != null) {
            UI.printHeader("Messages from " + user);
            for (String msg : messages) {
                System.out.println(UI.WHITE + "- \"" + msg + "\"" + UI.RESET);
            }
        } else {
            UI.printError("No messages from that user.");
        }
    }

    private void acceptChatRequest(String user) {
        if (!messageRequests.containsKey(user)) {
            UI.printError("No request from that user.");
            return;
        }
        
        List<String> messages = messageRequests.remove(user);
        currentChatPartner = user;
        currentState = AppState.IN_CHAT; // Change state to IN_CHAT

        // Send an ACCEPT_REQUEST message so they also enter the chat state
        sendMessage(user, "I've accepted your chat request. Let's talk!", Message.MessageType.ACCEPT_REQUEST);
        
        UI.printHeader("Chat with " + user);
        UI.printSystem("Type 'quit' to exit the chat.\n");
        // Print the message history that started this chat
        for (String msg : messages) {
            UI.printChat(user, msg);
        }
    }

    private void sendMessageRequest(String targetUsername, String message) {
        if (username.equals(targetUsername)) {
            UI.printError("You can't chat with yourself.");
            return;
        }
        sendMessage(targetUsername, message, Message.MessageType.REQUEST);
        UI.printSystem("Message request sent to '" + targetUsername + "'.");
    }

    private void sendChatMessage(String targetUsername, String message) {
        sendMessage(targetUsername, message, Message.MessageType.CHAT);
    }

    /**
     * The core sending logic. Connects to the peer and sends a Message object.
     */
    private void sendMessage(String targetUsername, String message, Message.MessageType type) {
        PeerDiscovery.DiscoveredPeer peer = peerDiscovery.getPeer(targetUsername);
        if (peer == null) {
            UI.printError("User '" + targetUsername + "' is not online or discoverable.");
            return;
        }

        try (Socket socket = new Socket(peer.ip, peer.port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            
            byte[] encryptedContent = CryptoUtils.encrypt(message, peer.publicKey);
            Message msgObject = new Message(type, username, encryptedContent);
            out.writeObject(msgObject);
            out.flush();

        } catch (Exception e) {
            UI.printError("Error sending message to " + targetUsername + ": " + e.getMessage());
        }
    }
    
    private void stop() {
        UI.printSystem("Shutting down...");
        this.running = false;
        peerDiscovery.stop();
        executorService.shutdownNow();
        System.exit(0);
    }

    // --- ASYNCHRONOUS (NETWORK-THREAD) METHODS ---

    /**
     * Called by ConnectionHandler when a new request arrives.
     * Synchronized to prevent conflicts with the UI thread.
     */
    public void addMessageRequest(String fromUser, String message) {
        synchronized (printLock) {
            messageRequests.computeIfAbsent(fromUser, k -> new ArrayList<>()).add(message);
            // Only print notification if user is in main menu
            if (currentState == AppState.MAIN_MENU) {
                UI.printNotification("New request from '" + fromUser + "'. Type 'requests' to view.");
            }
        }
    }

    /**
     * Called by ConnectionHandler when a chat partner accepts our request.
     * Forces the user into the chat state.
     */
    public void startChatSession(String partner, String initialMessage) {
        synchronized (printLock) {
            currentChatPartner = partner;
            currentState = AppState.IN_CHAT;
            UI.printHeader("Chat with " + partner);
            UI.printSystem("Type 'quit' to exit the chat.\n");
            UI.printChat(partner, initialMessage);
            System.out.print(UI.CHAT_PROMPT); // Print first chat prompt
        }
    }

    /**
     * Called by ConnectionHandler when a chat message arrives.
     */
    public void displayChatMessage(String fromUser, String message) {
        synchronized (printLock) {
            UI.printChat(fromUser, message);
            System.out.print(UI.CHAT_PROMPT); // Re-print prompt
        }
    }

    public boolean isInChatWith(String partner) {
        return partner != null && partner.equals(currentChatPartner);
    }
}
