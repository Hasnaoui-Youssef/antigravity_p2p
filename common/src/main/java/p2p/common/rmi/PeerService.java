package p2p.common.rmi;

import p2p.common.model.message.Message;

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

}
