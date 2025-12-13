package p2p.bootstrap;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Objects;

/**
 * UDP listener that receives heartbeat packets from peers.
 * Packet format: HEARTBEAT|userId|username|ip|port
 */
public class HeartbeatListener implements Runnable {

    public static final int DEFAULT_UDP_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;

    private final UserRegistry userRegistry;
    private final DatagramSocket socket;
    private final int udpPort;
    private volatile boolean running = true;

    /**
     * Creates a HeartbeatListener on the default UDP port.
     */
    public HeartbeatListener(UserRegistry userRegistry) throws SocketException {
        this(userRegistry, DEFAULT_UDP_PORT);
    }

    /**
     * Creates a HeartbeatListener on the specified UDP port.
     *
     * @param userRegistry The user registry to update with heartbeats
     * @param udpPort The UDP port to listen on
     */
    public HeartbeatListener(UserRegistry userRegistry, int udpPort) throws SocketException {
        this.userRegistry = Objects.requireNonNull(userRegistry, "userRegistry must not be null");
        if (udpPort < 1 || udpPort > 65535) {
            throw new IllegalArgumentException("UDP port must be between 1 and 65535");
        }
        this.udpPort = udpPort;
        this.socket = new DatagramSocket(udpPort);
        System.out.println("[Heartbeat] UDP listener started on port " + udpPort);
    }

    /**
     * Gets the UDP port this listener is bound to.
     */
    public int getUdpPort() {
        return udpPort;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                processHeartbeat(message);

            } catch (Exception e) {
                if (running) {
                    System.err.println("[Heartbeat] Error receiving packet: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Process a heartbeat message and update the registry.
     */
    private void processHeartbeat(String message) {
        try {
            String[] parts = message.split("\\|");
            if (parts.length >= 2 && "HEARTBEAT".equals(parts[0])) {
                String userId = parts[1];
                userRegistry.updateHeartbeat(userId);
            }
        } catch (Exception e) {
            System.err.println("[Heartbeat] Error processing heartbeat: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        socket.close();
    }
}
