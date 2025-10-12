package com.shiva.p2pchat.model;

import java.security.PublicKey;

public class Contact {
    private final String username;
    private final String ipAddress;
    private final int port;
    private final transient PublicKey publicKey; // 'transient' so Gson ignores it
    private final String publicKeyString; // Store the key as a Base64 string for JSON

    public Contact(String username, String ipAddress, int port, PublicKey publicKey, String publicKeyString) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
        this.publicKey = publicKey;
        this.publicKeyString = publicKeyString;
    }

    // Getters
    public String getUsername() { return username; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public PublicKey getPublicKey() { return publicKey; }
    public String getPublicKeyString() { return publicKeyString; }
}