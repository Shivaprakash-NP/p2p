package com.shiva.p2pchat.discovery;

import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shiva.p2pchat.crypto.CryptoUtils;
import com.shiva.p2pchat.ui.UI;

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
        // We now use two separate sockets: one for listening, one for broadcasting
        Thread listenerThread = new Thread(this::listenForPeers);
        Thread broadcastThread = new Thread(this::broadcastPresence);

        listenerThread.setDaemon(true);
        broadcastThread.setDaemon(true);

        listenerThread.start();
        broadcastThread.start();
    }

    /**
     * Tries to find the correct broadcast address for the local network.
     * Falls back to 255.255.255.255 if one isn't found.
     */
    private InetAddress findBroadcastAddress() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue; // Skip loopback and down interfaces
            }

            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                InetAddress broadcast = interfaceAddress.getBroadcast();
                if (broadcast != null) {
                    return broadcast; // Found a valid broadcast address
                }
            }
        }
        // Fallback to global broadcast
        try {
            return InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            return null; // Should never happen
        }
    }

    /**
     * This thread handles broadcasting our presence to the network.
     */
    private void broadcastPresence() {
        try (DatagramSocket broadcastSocket = new DatagramSocket()) {
            broadcastSocket.setBroadcast(true);

            InetAddress broadcastAddress = findBroadcastAddress();
            if (broadcastAddress == null) {
                UI.printError("Could not find broadcast address. Discovery may fail.");
                return;
            }
            UI.printSystem("Broadcasting to: " + broadcastAddress.getHostAddress());


            Map<String, String> broadcastData = new HashMap<>();
            broadcastData.put("username", username);
            broadcastData.put("port", String.valueOf(tcpPort));
            broadcastData.put("publicKey", publicKeyStr);
            String message = gson.toJson(broadcastData);
            byte[] sendData = message.getBytes();

            while (running) {
                try {
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcastAddress, discoveryPort);
                    broadcastSocket.send(sendPacket);
                    Thread.sleep(5000); // Broadcast every 5 seconds
                } catch (Exception e) {
                    // Ignore transient send errors
                }
            }
        } catch (Exception e) {
            UI.printError("Broadcast service failed: " + e.getMessage());
        }
    }

    /**
     * This thread handles listening for broadcasts from other peers.
     */
    private void listenForPeers() {
        try (DatagramSocket listenerSocket = new DatagramSocket(discoveryPort)) {
            byte[] recvBuf = new byte[2048]; // Increased buffer for public key

            while (running) {
                try {
                    DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
                    listenerSocket.receive(receivePacket);

                    String jsonMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    Type type = new TypeToken<Map<String, String>>() {}.getType();
                    Map<String, String> receivedData = gson.fromJson(jsonMessage, type);

                    String peerUsername = receivedData.get("username");
                    if (peerUsername != null && !peerUsername.equals(username)) {
                        int peerPort = Integer.parseInt(receivedData.get("port"));
                        String peerIp = receivePacket.getAddress().getHostAddress();
                        PublicKey peerPublicKey = CryptoUtils.stringToPublicKey(receivedData.get("publicKey"));

                        if (!onlinePeers.containsKey(peerUsername)) {
                            // This is a UI-sensitive operation, but a simple print is okay
                            System.out.println(UI.YELLOW + "\n[SYSTEM] " + peerUsername + " joined the network." + UI.RESET);
                        }
                        onlinePeers.put(peerUsername, new DiscoveredPeer(peerIp, peerPort, peerPublicKey));
                    }
                } catch (Exception e) {
                    // Ignore malformed packets
                }
            }
        } catch (Exception e) {
            UI.printError("Discovery listener failed: " + e.getMessage());
        }
    }

    public Map<String, DiscoveredPeer> getOnlinePeers() {
        // Simple timeout logic: remove peers not seen in 15 seconds
        long now = System.currentTimeMillis();
        onlinePeers.entrySet().removeIf(entry -> (now - entry.getValue().lastSeen) > 15000);
        return new ConcurrentHashMap<>(onlinePeers);
    }

    public DiscoveredPeer getPeer(String username) {
        return getOnlinePeers().get(username);
    }

    public void stop() {
        this.running = false;
    }
}

