package p2p.peer.network;

import p2p.common.model.User;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Sends periodic UDP heartbeats to the bootstrap server.
 */
public class HeartbeatSender implements Runnable {

    private static final int HEARTBEAT_INTERVAL_MS = 10_000; // 10 seconds

    private final User localUser;
    private final String bootstrapHost;
    private final int bootstrapPort;
    private volatile boolean running = true;

    public HeartbeatSender(User localUser, String bootstrapHost, int bootstrapPort) {
        this.localUser = localUser;
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(bootstrapHost);

            while (running) {
                try {
                    // Format: HEARTBEAT|userId|username|ip|port
                    DatagramPacket packet = getDatagramPacket(address);
                    socket.send(packet);

                    Thread.sleep(HEARTBEAT_INTERVAL_MS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[Heartbeat] Error sending: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Heartbeat] Failed to create socket: " + e.getMessage());
        }
    }

    private DatagramPacket getDatagramPacket(InetAddress address) {
        String message = String.format("HEARTBEAT|%s|%s|%s|%d",
                localUser.userId(),
                localUser.username(),
                localUser.ipAddress(),
                localUser.rmiPort()
        );

        byte[] buffer = message.getBytes();
        return new DatagramPacket(buffer, buffer.length, address, bootstrapPort);
    }

    public void stop() {
        running = false;
    }
}
