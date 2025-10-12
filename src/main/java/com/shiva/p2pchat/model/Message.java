package com.shiva.p2pchat.model;

import java.io.Serializable;
import java.time.Instant;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum MessageType {
        REQUEST, CHAT, ACCEPT_REQUEST
    }

    private final MessageType type;
    private final String senderUsername;
    private final byte[] encryptedContent;
    private final long timestamp;

    public Message(MessageType type, String senderUsername, byte[] encryptedContent) {
        this.type = type;
        this.senderUsername = senderUsername;
        this.encryptedContent = encryptedContent;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public MessageType getType() { return type; }
    public String getSenderUsername() { return senderUsername; }
    public byte[] getEncryptedContent() { return encryptedContent; }
    public long getTimestamp() { return timestamp; }
}