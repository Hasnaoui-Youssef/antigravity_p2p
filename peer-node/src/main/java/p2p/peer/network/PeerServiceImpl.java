package p2p.peer.network;

import p2p.common.model.*;
import p2p.common.model.message.*;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.consensus.ConsensusManager;
import p2p.peer.friends.FriendManager;
import p2p.peer.groups.GroupManager;
import p2p.peer.groups.LeaderElectionManager;
import p2p.peer.messaging.GossipManager;
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
    private final GroupManager groupManager;
    private final GossipManager gossipManager;
    private final ConsensusManager consensusManager;
    private LeaderElectionManager electionManager;
    
    public PeerServiceImpl(User localUser, VectorClock vectorClock, 
                          FriendManager friendManager, MessageHandler messageHandler,
                          GroupManager groupManager, GossipManager gossipManager,
                          ConsensusManager consensusManager) 
            throws RemoteException {
        super();
        this.localUser = localUser;
        this.vectorClock = vectorClock;
        this.friendManager = friendManager;
        this.messageHandler = messageHandler;
        this.groupManager = groupManager;
        this.gossipManager = gossipManager;
        this.consensusManager = consensusManager;
    }
    
    public void setElectionManager(LeaderElectionManager electionManager) {
        this.electionManager = electionManager;
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
        // Handle different message types
        switch (message.getType()) {
            case DIRECT -> handleDirectMessage((DirectMessage) message);
            case GROUP -> handleGroupMessage((GroupMessage) message);
            case GOSSIP -> handleGossipMessage((GossipMessage) message);
            case SYNC_REQUEST -> handleSyncRequest((SyncRequest) message);
            case SYNC_RESPONSE -> handleSyncResponse((SyncResponse) message);
            case ELECTION -> handleElectionMessage((ElectionMessage) message);
            case INVITATION_REQUEST -> handleInvitationRequest((GroupInvitationRequest) message);
            case INVITATION_RESPONSE -> handleInvitationResponse((GroupInvitationResponse) message);
        }
    }
    
    private void handleDirectMessage(DirectMessage message) {
        synchronized (vectorClock) {
            vectorClock.update(message.getVectorClock());
        }
        messageHandler.handleIncomingMessage(message);
    }
    
    private void handleGroupMessage(GroupMessage message) {
        synchronized (vectorClock) {
            vectorClock.update(message.getVectorClock());
        }
        // Store in group and display
        groupManager.addMessage(message.getGroupId(), message);
        System.out.println("\n[Group: " + message.getGroupId() + "] " + 
            message.getSenderUsername() + ": " + message.getContent());
        System.out.print("> ");
    }
    
    private void handleGossipMessage(GossipMessage message) {
        if (gossipManager != null) {
            gossipManager.handleGossipMessage(message);
        }
    }
    
    /**
     * Find user by ID from friends list.
     */
    private User findUserBySenderId(String senderId) {
        return friendManager.getFriends().stream()
            .filter(u -> u.getUserId().equals(senderId))
            .findFirst()
            .orElse(null);
    }
    
    private void handleSyncRequest(SyncRequest message) {
        // Find requester
        User requester = findUserBySenderId(message.getSenderId());
        if (requester != null && consensusManager != null) {
            consensusManager.handleSyncRequest(message.getGroupId(), message.getLastKnownState(), requester, message.getMessageId());
        }
    }
    
    private void handleSyncResponse(SyncResponse message) {
        if (consensusManager != null) {
            consensusManager.handleSyncResponse(message);
        }
    }
    
    private void handleElectionMessage(ElectionMessage message) {
        if (electionManager == null) {
            return;
        }
        switch (message.getElectionType()) {
            case PROPOSAL -> {
                // Received election proposal
                electionManager.handleElectionProposal(message);
            }
            case VOTE -> {
                // Received vote
                electionManager.handleElectionVote(message);
            }
            case RESULT -> {
                // Received election result
                electionManager.handleElectionResult(message);
            }
        }
    }
    
    private void handleInvitationRequest(GroupInvitationRequest message) {
        if (groupManager != null) {
            groupManager.handleInvitationRequest(message);
        }
    }
    
    private void handleInvitationResponse(GroupInvitationResponse message) {
        if (groupManager != null) {
            groupManager.handleInvitationResponse(message);
        }
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
    
    @Override
    public void addFinalizedGroup(Group group) throws RemoteException {
        if (groupManager != null) {
            groupManager.addFinalizedGroup(group);
        }
    }
}
