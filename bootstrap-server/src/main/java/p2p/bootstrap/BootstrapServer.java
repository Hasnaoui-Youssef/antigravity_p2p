package p2p.bootstrap;

import p2p.common.rmi.BootstrapService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Main bootstrap server application.
 * Provides peer discovery and heartbeat monitoring.
 */
public class BootstrapServer {
    
    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "BootstrapService";
    
    public static void main(String[] args) {
        try {
            System.out.println("=== Antigravity P2P Bootstrap Server ===");
            
            // Create user registry
            UserRegistry userRegistry = new UserRegistry();
            
            // Start UDP heartbeat listener
            HeartbeatListener heartbeatListener = new HeartbeatListener(userRegistry);
            Thread heartbeatThread = new Thread(heartbeatListener, "HeartbeatListener");
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            
            // Create and export RMI service
            BootstrapService service = new BootstrapServiceImpl(userRegistry);
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            registry.rebind(SERVICE_NAME, service);
            
            System.out.println("[Bootstrap] RMI registry started on port " + RMI_PORT);
            System.out.println("[Bootstrap] Service bound as '" + SERVICE_NAME + "'");
            System.out.println("[Bootstrap] Server ready for peer connections");
            System.out.println();
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Bootstrap] Shutting down...");
                heartbeatListener.shutdown();
                userRegistry.shutdown();
            }));
            
            // Keep server running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("[Bootstrap] Server error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
