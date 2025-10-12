package com.shiva.p2pchat;

import java.util.Scanner;

import com.shiva.p2pchat.core.PeerNode;
import com.shiva.p2pchat.crypto.KeyManager;

public class Main {

    private static final int TCP_PORT = 8888; // Port for chat sessions
    private static final int DISCOVERY_PORT = 8889; // Port for peer discovery

    public static void main(String[] args) {
        System.out.println("--- P2P Secure Messenger ---");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your username: ");
        String username = scanner.nextLine();

        try {
            // Load or create cryptographic keys
            KeyManager keyManager = new KeyManager();
            keyManager.loadOrCreateKeys();
            
            System.out.println("\nWelcome, " + username + "!");
            System.out.println("Type 'mykey' to see your public key to share with others.");

            // Start the main peer node
            PeerNode node = new PeerNode(username, TCP_PORT, DISCOVERY_PORT, keyManager);
            node.start();

        } catch (Exception e) {
            System.err.println("A critical error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}