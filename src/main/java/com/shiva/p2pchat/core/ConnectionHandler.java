package com.shiva.p2pchat.core;

import java.io.ObjectInputStream;
import java.net.Socket;
import java.security.PrivateKey;

import com.shiva.p2pchat.crypto.CryptoUtils;
import com.shiva.p2pchat.model.Message;
import com.shiva.p2pchat.ui.UI;

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
        }
    }

    private void handleMessage(Message message) {
        try {
            String content = CryptoUtils.decrypt(message.getEncryptedContent(), privateKey);
            String sender = message.getSenderUsername();

            switch (message.getType()) {
                case REQUEST:
                    peerNode.addMessageRequest(sender, content);
                    break;
                case ACCEPT_REQUEST:
                    peerNode.startChatSession(sender, content);
                    break;
                case CHAT:
                    if (peerNode.isInChatWith(sender)) {
                        peerNode.displayChatMessage(sender, content);
                    } else {
                        peerNode.addMessageRequest(sender, content);
                    }
                    break;
            }
        } catch (Exception e) {
            UI.printError("Failed to handle message: " + e.getMessage());
        }
    }
}
