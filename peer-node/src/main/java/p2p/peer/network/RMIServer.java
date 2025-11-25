package p2p.peer.network;

import p2p.common.rmi.PeerService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Manages the RMI server for this peer.
 */
public class RMIServer {
    
    private final int port;
    private final String serviceName;
    private PeerService service;
    private Registry registry;
    
    public RMIServer(int port, String serviceName) {
        this.port = port;
        this.serviceName = serviceName;
    }
    
    /**
     * Start the RMI server and export the service.
     */
    public void start(PeerService serviceImpl) throws Exception {
        this.service = serviceImpl;
        this.registry = LocateRegistry.createRegistry(port);
        registry.rebind(serviceName, service);
        System.out.println("[RMI] Server started on port " + port);
    }
    
    /**
     * Stop the RMI server.
     */
    public void stop() {
        try {
            if (registry != null && serviceName != null) {
                registry.unbind(serviceName);
            }
        } catch (Exception e) {
            System.err.println("[RMI] Error stopping server: " + e.getMessage());
        }
    }
}
