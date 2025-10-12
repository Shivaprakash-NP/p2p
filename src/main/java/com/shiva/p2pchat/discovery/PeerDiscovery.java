package com.shiva.p2pchat.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeerDiscovery implements Runnable {

    private final String username;
    private final int tcpPort;
    private final int discoveryPort;
    private final Map<String, DiscoveredPeer> onlinePeers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean running = true;

    // Helper class to store peer info and last seen time
    private static class DiscoveredPeer {
        String ip;
        int port;
        long lastSeen;
        DiscoveredPeer(String ip, int port) {
            this.ip = ip;
            this.port = port;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    public PeerDiscovery(String username, int tcpPort, int discoveryPort) {
        this.username = username;
        this.tcpPort = tcpPort;
        this.discoveryPort = discoveryPort;
    }

    @Override
    public void run() {
        // Schedule cleanup task to remove offline peers
        scheduler.scheduleAtFixedRate(this::removeInactivePeers, 10, 10, TimeUnit.SECONDS);

        try (DatagramSocket socket = new DatagramSocket(discoveryPort)) {
            socket.setBroadcast(true);

            // Thread for broadcasting our presence
            Thread broadcastThread = new Thread(() -> {
                while (running) {
                    try {
                        String message = "DISCOVERY:" + username + ":" + tcpPort;
                        byte[] sendData = message.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), discoveryPort);
                        socket.send(sendPacket);
                        Thread.sleep(5000); // Broadcast every 5 seconds
                    } catch (Exception e) {
                        // Suppress errors in broadcast loop
                    }
                }
            });
            broadcastThread.start();

            // Main loop for listening to others' broadcasts
            byte[] recvBuf = new byte[1024];
            while (running) {
                DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(receivePacket);
                String message = new String(receivePacket.getData()).trim();

                if (message.startsWith("DISCOVERY:")) {
                    String[] parts = message.split(":");
                    if (parts.length == 3) {
                        String peerUsername = parts[1];
                        int peerPort = Integer.parseInt(parts[2]);
                        String peerIp = receivePacket.getAddress().getHostAddress();

                        if (!peerUsername.equals(username)) { // Don't add ourselves
                           onlinePeers.put(peerUsername, new DiscoveredPeer(peerIp, peerPort));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Peer Discovery failed: " + e.getMessage());
        } finally {
            scheduler.shutdown();
        }
    }
    
    private void removeInactivePeers() {
        long now = System.currentTimeMillis();
        onlinePeers.entrySet().removeIf(entry -> (now - entry.getValue().lastSeen) > 15000); // 15-second timeout
    }

    public Map<String, DiscoveredPeer> getOnlinePeers() {
        return new ConcurrentHashMap<>(onlinePeers); // Return a copy
    }

    public String getPeerIp(String username) {
        DiscoveredPeer peer = onlinePeers.get(username);
        return (peer != null) ? peer.ip : null;
    }

    public int getPeerPort(String username) {
        DiscoveredPeer peer = onlinePeers.get(username);
        return (peer != null) ? peer.port : -1;
    }

    public void stop() {
        this.running = false;
        scheduler.shutdownNow();
    }
}