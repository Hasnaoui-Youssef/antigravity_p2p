package p2p.peer.network;

import p2p.common.model.Message;
import p2p.common.model.User;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.friends.FriendManager;
import p2p.peer.messaging.MessageHandler;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Implementation of PeerService RMI interface.
 * Handles incoming requests from other peers.
 */
public class PeerServiceImpl extends UnicastRemoteObject implements PeerService {
    
    private final User localUser;
    private final VectorClock vectorClock;
    private final FriendManager friendManager;
    private final MessageHandler messageHandler;
    
    public PeerServiceImpl(User localUser, VectorClock vectorClock, 
                          FriendManager friendManager, MessageHandler messageHandler) 
            throws RemoteException {
        super();
        this.localUser = localUser;
        this.vectorClock = vectorClock;
        this.friendManager = friendManager;
        this.messageHandler = messageHandler;
    }
    
    @Override
    public void receiveFriendRequest(User requester, VectorClock clock) throws RemoteException {
        synchronized (vectorClock) {
            vectorClock.update(clock);
        }
        friendManager.handleFriendRequest(requester);
        System.out.println("\n[Friend Request] From: " + requester.getUsername());
        System.out.println("Use '/accept " + requester.getUsername() + "' to accept");
        System.out.print("> ");
    }
    
    @Override
    public void acceptFriendRequest(User accepter, VectorClock clock) throws RemoteException {
        synchronized (vectorClock) {
            vectorClock.update(clock);
        }
        friendManager.handleFriendAcceptance(accepter);
        System.out.println("\n[Friend Accepted] " + accepter.getUsername() + " accepted your friend request!");
        System.out.print("> ");
    }
    
    @Override
    public void receiveMessage(Message message) throws RemoteException {
        synchronized (vectorClock) {
            vectorClock.update(message.getVectorClock());
        }
        messageHandler.handleIncomingMessage(message);
    }
    
    @Override
    public void updateVectorClock(VectorClock clock) throws RemoteException {
        synchronized (vectorClock) {
            vectorClock.update(clock);
        }
    }
    
    @Override
    public boolean ping() throws RemoteException {
        return true;
    }
}
