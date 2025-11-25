package p2p.bootstrap;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * UDP listener that receives heartbeat packets from peers.
 * Packet format: HEARTBEAT|userId|username|ip|port
 */
public class HeartbeatListener implements Runnable {
    
    private static final int UDP_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;
    
    private final UserRegistry userRegistry;
    private final DatagramSocket socket;
    private volatile boolean running = true;
    
    public HeartbeatListener(UserRegistry userRegistry) throws SocketException {
        this.userRegistry = userRegistry;
        this.socket = new DatagramSocket(UDP_PORT);
        System.out.println("[Heartbeat] UDP listener started on port " + UDP_PORT);
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
