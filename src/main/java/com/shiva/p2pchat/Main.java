package com.shiva.p2pchat;

import java.util.Scanner;

import com.shiva.p2pchat.core.PeerNode;
import com.shiva.p2pchat.crypto.KeyManager;
import com.shiva.p2pchat.ui.UI;

public class Main {

    private static final int TCP_PORT = 8888;
    private static final int DISCOVERY_PORT = 8889;

    public static void main(String[] args) {
        UI.printHeader("P2P SECURE MESSENGER");

        Scanner scanner = new Scanner(System.in);
        System.out.print(UI.CYAN + "Enter your username: " + UI.RESET);
        String username = scanner.nextLine();

        if (username.isEmpty() || username.contains(" ")) {
            UI.printError("Username cannot be empty or contain spaces.");
            return;
        }

        try {
            KeyManager keyManager = new KeyManager();
            keyManager.loadOrCreateKeys();
            
            System.out.println(UI.GREEN + "\nWelcome, " + UI.BOLD + username + UI.RESET + UI.GREEN + "! Searching for peers..." + UI.RESET);

            PeerNode node = new PeerNode(username, TCP_PORT, DISCOVERY_PORT, keyManager);
            node.start();

        } catch (Exception e) {
            UI.printError("A critical error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
