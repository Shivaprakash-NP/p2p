package com.shiva.p2pchat.core;

import com.shiva.p2pchat.crypto.CryptoUtils;
import com.shiva.p2pchat.model.Message;
import com.shiva.p2pchat.ui.AnsiColors;

import java.io.ObjectInputStream;
import java.net.Socket;
import java.security.PrivateKey;

public class ConnectionHandler implements Runnable {

    private final Socket socket;
    private final PeerNode peerNode;
    private final PrivateKey privateKey;

    public ConnectionHandler(Socket socket, PeerNode peerNode) {
        this.socket = socket;
        this.peerNode = peerNode;
        this.privateKey = peerNode.getKeyManager().getPrivateKey();
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (!socket.isClosed()) {
                Message message = (Message) in.readObject();
                handleMessage(message);
            }
        } catch (Exception e) {
            // Connection closed or error
        }
    }

    private void handleMessage(Message message) {
        try {
            String content = CryptoUtils.decrypt(message.getEncryptedContent(), privateKey);
            String sender = message.getSenderUsername();

            switch (message.getType()) {
                case REQUEST:
                    peerNode.addMessageRequest(sender, content);
                    System.out.println(AnsiColors.ANSI_YELLOW + "\n[SYSTEM] You have a new message request from '" + sender + "'. Type 'inbox' to view." + AnsiColors.ANSI_RESET);
                    System.out.print(AnsiColors.ANSI_CYAN + "\n> " + AnsiColors.ANSI_RESET);
                    break;
                case ACCEPT_REQUEST:
                    peerNode.startChatSession(sender);
                    System.out.println(AnsiColors.ANSI_YELLOW + "\n[SYSTEM] '" + sender + "' accepted your chat request. You are now in a chat." + AnsiColors.ANSI_RESET);
                    System.out.println("--- Chat with " + sender + " ---");
                    break;
                case CHAT:
                    if (peerNode.isInChatWith(sender)) {
                        System.out.println(AnsiColors.ANSI_GREEN + "\n[" + sender + "]: " + content + AnsiColors.ANSI_RESET);
                        System.out.print(AnsiColors.ANSI_CYAN + "> " + AnsiColors.ANSI_RESET);
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Failed to handle message: " + e.getMessage());
        }
    }
}