package p2p.bootstrap;

import p2p.common.model.User;
import p2p.common.rmi.BootstrapService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * Implementation of the BootstrapService RMI interface.
 */
public class BootstrapServiceImpl extends UnicastRemoteObject implements BootstrapService {
    
    private final UserRegistry userRegistry;
    
    public BootstrapServiceImpl(UserRegistry userRegistry) throws RemoteException {
        super();
        this.userRegistry = userRegistry;
    }
    
    @Override
    public void register(User user) throws RemoteException {
        userRegistry.addUser(user);
    }
    
    @Override
    public void unregister(String userId) throws RemoteException {
        userRegistry.removeUser(userId);
    }
    
    @Override
    public List<User> searchByUsername(String username) throws RemoteException {
        return userRegistry.searchByUsername(username);
    }
    
    @Override
    public List<User> searchByIp(String ip) throws RemoteException {
        return userRegistry.searchByIp(ip);
    }
    
    @Override
    public List<User> getAllUsers() throws RemoteException {
        return userRegistry.getAllUsers();
    }
}
