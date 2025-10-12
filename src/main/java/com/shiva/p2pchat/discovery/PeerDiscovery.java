package com.shiva.p2pchat.discovery;

import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shiva.p2pchat.crypto.CryptoUtils;
import com.shiva.p2pchat.ui.AnsiColors;

public class PeerDiscovery implements Runnable {

    private final String username;
    private final int tcpPort;
    private final String publicKeyStr;
    private final int discoveryPort;
    private final Map<String, DiscoveredPeer> onlinePeers = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private volatile boolean running = true;

    public static class DiscoveredPeer {
        public String ip;
        public int port;
        public PublicKey publicKey;
        public long lastSeen;

        public DiscoveredPeer(String ip, int port, PublicKey publicKey) {
            this.ip = ip;
            this.port = port;
            this.publicKey = publicKey;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    public PeerDiscovery(String username, int tcpPort, int discoveryPort, String publicKeyStr) {
        this.username = username;
        this.tcpPort = tcpPort;
        this.discoveryPort = discoveryPort;
        this.publicKeyStr = publicKeyStr;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(discoveryPort)) {
            socket.setBroadcast(true);

            Thread broadcastThread = new Thread(() -> broadcastPresence(socket));
            broadcastThread.setDaemon(true);
            broadcastThread.start();

            listenForPeers(socket);

        } catch (Exception e) {
            System.err.println("Peer Discovery failed: " + e.getMessage());
        }
    }

    private void broadcastPresence(DatagramSocket socket) {
        Map<String, String> broadcastData = new HashMap<>();
        broadcastData.put("username", username);
        broadcastData.put("port", String.valueOf(tcpPort));
        broadcastData.put("publicKey", publicKeyStr);
        String message = gson.toJson(broadcastData);
        byte[] sendData = message.getBytes();

        while (running) {
            try {
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), discoveryPort);
                socket.send(sendPacket);
                Thread.sleep(5000);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void listenForPeers(DatagramSocket socket) {
        byte[] recvBuf = new byte[2048]; // Increased buffer for public key
        while (running) {
            try {
                DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(receivePacket);

                String jsonMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> receivedData = gson.fromJson(jsonMessage, type);

                String peerUsername = receivedData.get("username");
                if (peerUsername != null && !peerUsername.equals(username)) {
                    int peerPort = Integer.parseInt(receivedData.get("port"));
                    String peerIp = receivePacket.getAddress().getHostAddress();
                    PublicKey peerPublicKey = CryptoUtils.stringToPublicKey(receivedData.get("publicKey"));

                    if (!onlinePeers.containsKey(peerUsername)) {
                        System.out.println(AnsiColors.ANSI_YELLOW + "\n[SYSTEM] Found user '" + peerUsername + "'" + AnsiColors.ANSI_RESET);
                        System.out.print(AnsiColors.ANSI_CYAN + "\n> " + AnsiColors.ANSI_RESET);
                    }
                    onlinePeers.put(peerUsername, new DiscoveredPeer(peerIp, peerPort, peerPublicKey));
                }
            } catch (Exception e) {
                // Ignore malformed packets
            }
        }
    }

    public Map<String, DiscoveredPeer> getOnlinePeers() {
        return new ConcurrentHashMap<>(onlinePeers);
    }

    public DiscoveredPeer getPeer(String username) {
        return onlinePeers.get(username);
    }

    public void stop() {
        this.running = false;
    }
}