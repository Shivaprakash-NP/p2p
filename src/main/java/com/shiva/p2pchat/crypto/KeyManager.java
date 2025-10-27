package com.shiva.p2pchat.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class KeyManager {

    private static final Path PUBLIC_KEY_PATH = Paths.get("data/user/public.key");
    private static final Path PRIVATE_KEY_PATH = Paths.get("data/user/private.key");

    private PublicKey publicKey;
    private PrivateKey privateKey;

    public void loadOrCreateKeys() throws Exception {
        Files.createDirectories(PUBLIC_KEY_PATH.getParent());
        
        if (Files.exists(PUBLIC_KEY_PATH) && Files.size(PUBLIC_KEY_PATH) > 0 && 
            Files.exists(PRIVATE_KEY_PATH) && Files.size(PRIVATE_KEY_PATH) > 0) {
            
            System.out.println("Loading existing keys...");
            String publicKeyStr = new String(Files.readAllBytes(PUBLIC_KEY_PATH));
            String privateKeyStr = new String(Files.readAllBytes(PRIVATE_KEY_PATH));
            this.publicKey = CryptoUtils.stringToPublicKey(publicKeyStr);
            this.privateKey = CryptoUtils.stringToPrivateKey(privateKeyStr);
            
        } else {
            System.out.println("No valid keys found. Generating new key pair...");
            KeyPair keyPair = CryptoUtils.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
            saveKeys();
            System.out.println("New keys generated and saved.");
        }
    }

    private void saveKeys() throws IOException {
        Files.write(PUBLIC_KEY_PATH, CryptoUtils.keyToString(publicKey).getBytes());
        Files.write(PRIVATE_KEY_PATH, CryptoUtils.keyToString(privateKey).getBytes());
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}