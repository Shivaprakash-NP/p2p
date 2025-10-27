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
        Thread listenerThread = new Thread(this::listenForPeers);
        Thread broadcastThread = new Thread(this::broadcastPresence);

        listenerThread.setDaemon(true);
        broadcastThread.setDaemon(true);

        listenerThread.start();
        broadcastThread.start();
    }

    private InetAddress findBroadcastAddress() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue; 
            }

            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                InetAddress broadcast = interfaceAddress.getBroadcast();
                if (broadcast != null) {
                    return broadcast; 
                }
            }
        }
        try {
            return InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            return null; 
        }
    }


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
                }
            }
        } catch (Exception e) {
            UI.printError("Broadcast service failed: " + e.getMessage());
        }
    }

    private void listenForPeers() {
        try (DatagramSocket listenerSocket = new DatagramSocket(discoveryPort)) {
            byte[] recvBuf = new byte[2048]; 

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
                            System.out.println(UI.YELLOW + "\n[SYSTEM] " + peerUsername + " joined the network." + UI.RESET);
                        }
                        onlinePeers.put(peerUsername, new DiscoveredPeer(peerIp, peerPort, peerPublicKey));
                    }
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            UI.printError("Discovery listener failed: " + e.getMessage());
        }
    }

    public Map<String, DiscoveredPeer> getOnlinePeers() {
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

