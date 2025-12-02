package p2p.common.rmi;

import p2p.common.model.Group;
import p2p.common.model.message.Message;
import p2p.common.vectorclock.VectorClock;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI interface for peer-to-peer communication.
 * Each peer implements this interface to receive requests from other peers.
 */
public interface PeerService extends Remote {

    /**
     * Receive a generic message (Direct, Group, Gossip, etc.).
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
