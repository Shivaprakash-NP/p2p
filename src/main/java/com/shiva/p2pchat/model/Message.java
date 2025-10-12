package com.shiva.p2pchat.model;

import java.io.Serializable;
import java.time.Instant;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L; // For serialization consistency

    private final String senderUsername;
    private final byte[] encryptedContent;
    private final long timestamp;

    public Message(String senderUsername, byte[] encryptedContent) {
        this.senderUsername = senderUsername;
        this.encryptedContent = encryptedContent;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public byte[] getEncryptedContent() {
        return encryptedContent;
    }

    public long getTimestamp() {
        return timestamp;
    }
}