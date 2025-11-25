package p2p.common.rmi;

import p2p.common.model.User;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI interface for bootstrap server operations.
 * Handles user registration, discovery, and search.
 */
public interface BootstrapService extends Remote {
    
    /**
     * Register a user with the bootstrap server.
     * Called when a peer joins the network.
     */
    void register(User user) throws RemoteException;
    
    /**
     * Unregister a user from the bootstrap server.
     * Called when a peer explicitly leaves the network.
     */
    void unregister(String userId) throws RemoteException;
    
    /**
     * Search for users by username (partial match).
     */
    List<User> searchByUsername(String username) throws RemoteException;
    
    /**
     * Search for users by IP address (partial match).
     */
    List<User> searchByIp(String ip) throws RemoteException;
    
    /**
     * Get all currently registered users.
     */
    List<User> getAllUsers() throws RemoteException;
}
