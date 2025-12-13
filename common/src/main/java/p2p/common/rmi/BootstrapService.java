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
     *
     * @param user The user to register
     * @throws RemoteException if RMI communication fails
     * @throws UsernameAlreadyExistsException if the username is already taken by another user
     */
    void register(User user) throws RemoteException, UsernameAlreadyExistsException;

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

    /**
     * Check if a username is available for registration.
     *
     * @param username The username to check
     * @return true if the username is available, false if already taken
     */
    boolean isUsernameAvailable(String username) throws RemoteException;
}
