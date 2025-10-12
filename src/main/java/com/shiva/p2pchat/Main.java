package com.shiva.p2pchat;

import java.util.Scanner;

import com.shiva.p2pchat.core.PeerNode;
import com.shiva.p2pchat.crypto.KeyManager;
import com.shiva.p2pchat.ui.AnsiColors;

public class Main {

    private static final int TCP_PORT = 8888;
    private static final int DISCOVERY_PORT = 8889;

    public static void main(String[] args) {
        System.out.println(AnsiColors.ANSI_BOLD + AnsiColors.ANSI_YELLOW);
        System.out.println("============================");
        System.out.println("=== P2P SECURE MESSENGER ===");
        System.out.println("============================" + AnsiColors.ANSI_RESET);

        Scanner scanner = new Scanner(System.in);
        System.out.print(AnsiColors.ANSI_CYAN + "Enter your username: " + AnsiColors.ANSI_RESET);
        String username = scanner.nextLine();

        try {
            KeyManager keyManager = new KeyManager();
            keyManager.loadOrCreateKeys();
            
            System.out.println("\nWelcome, " + username + "! Searching for peers...");

            PeerNode node = new PeerNode(username, TCP_PORT, DISCOVERY_PORT, keyManager);
            node.start();

        } catch (Exception e) {
            System.err.println("A critical error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}