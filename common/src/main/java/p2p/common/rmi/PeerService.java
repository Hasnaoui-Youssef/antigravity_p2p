package p2p.common.rmi;

import p2p.common.model.Message;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI interface for peer-to-peer communication.
 * Each peer implements this interface to receive requests from other peers.
 */
public interface PeerService extends Remote {
    
    /**
     * Receive a friend request from another peer.
     * 
     * @param requester The user sending the friend request
     * @param clock The requester's vector clock
     */
    void receiveFriendRequest(User requester, VectorClock clock) throws RemoteException;
    
    /**
     * Receive notification that a friend request was accepted.
     * 
     * @param accepter The user who accepted the request
     * @param clock The accepter's vector clock
     */
    void acceptFriendRequest(User accepter, VectorClock clock) throws RemoteException;
    
    /**
     * Receive a message from a friend.
     * 
     * @param message The message to receive
     */
    void receiveMessage(Message message) throws RemoteException;
    
    /**
     * Update vector clock (for synchronization purposes).
     * 
     * @param clock The updated vector clock
     */
    void updateVectorClock(VectorClock clock) throws RemoteException;
    
    /**
     * Ping to check if peer is alive.
     * 
     * @return true if peer is responsive
     */
    boolean ping() throws RemoteException;
}
