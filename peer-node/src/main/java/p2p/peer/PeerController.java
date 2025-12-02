package p2p.peer;

import p2p.common.model.Group;
import p2p.common.model.GroupEvent;
import p2p.common.model.MessageTopic;
import p2p.common.model.User;
import p2p.common.model.message.*;
import p2p.common.rmi.BootstrapService;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.consensus.ConsensusManager;
import p2p.peer.friends.FriendManager;
import p2p.peer.groups.GroupManager;
import p2p.peer.groups.LeaderElectionManager;
import p2p.peer.messaging.GossipManager;
import p2p.peer.messaging.MessageHandler;
import p2p.peer.network.HeartbeatSender;
import p2p.peer.network.RMIServer;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main controller for the peer node.
 * Merges the logic of the previous PeerController and PeerServiceImpl.
 * Acts as the RMI server implementation and the central coordination point.
 */
public class PeerController extends UnicastRemoteObject implements PeerService {

    private final User localUser;
    private final VectorClock vectorClock;
    private final FriendManager friendManager;
    private final MessageHandler messageHandler;
    private final GroupManager groupManager;
    private final GossipManager gossipManager;
    private final ConsensusManager consensusManager;
    private final LeaderElectionManager electionManager;
    private final RMIServer rmiServer;
    private final HeartbeatSender heartbeatSender;
    private final Thread heartbeatThread;

    private BootstrapService bootstrapService;
    private volatile boolean started = false;

    private final List<PeerEventListener> listeners = new CopyOnWriteArrayList<>();

    // Synchronization locks/latches for waiting
    private final Object messageWaitLock = new Object();
    private final Object groupWaitLock = new Object();
    private final Object electionWaitLock = new Object();

    /**
     * Creates a new peer controller.
     *
     * @param username      Peer username
     * @param rmiPort       RMI port for this peer
     * @param bootstrapHost Bootstrap server hostname
     * @param bootstrapPort Bootstrap server RMI port
     * @param udpPort       Bootstrap server UDP port for heartbeats
     */
    public PeerController(String username, int rmiPort, String bootstrapHost, int bootstrapPort, int udpPort)
            throws Exception {
        super(); // Export this object

        String localIp = InetAddress.getLocalHost().getHostAddress();
        this.localUser = User.create(username, localIp, rmiPort);

        this.vectorClock = new VectorClock();
        this.vectorClock.increment(localUser.userId());

        this.friendManager = new FriendManager(localUser, vectorClock);
        this.groupManager = new GroupManager(localUser, vectorClock, friendManager);
        this.messageHandler = new MessageHandler(localUser, vectorClock, friendManager);
        this.gossipManager = new GossipManager(localUser, groupManager);
        this.consensusManager = new ConsensusManager(localUser, groupManager, vectorClock);
        this.gossipManager.setConsensusManager(consensusManager);

        this.electionManager = new LeaderElectionManager(localUser, groupManager, vectorClock);
        this.electionManager.setGossipManager(gossipManager);
        this.groupManager.setElectionManager(electionManager);

        // We don't need PeerServiceImpl anymore, we ARE the service
        this.rmiServer = new RMIServer(rmiPort, "PeerService");

        this.heartbeatSender = new HeartbeatSender(localUser, bootstrapHost, udpPort);
        this.heartbeatThread = new Thread(heartbeatSender, "HeartbeatSender");
        this.heartbeatThread.setDaemon(true);

        Registry registry = LocateRegistry.getRegistry(bootstrapHost, bootstrapPort);
        this.bootstrapService = (BootstrapService) registry.lookup("BootstrapService");

        // Register internal listener to trigger notifyAll for waiters
        this.addEventListener(new InternalEventListener());
    }

    public void addEventListener(PeerEventListener listener) {
        listeners.add(listener);
        // Propagate listener to managers
        friendManager.addEventListener(listener);
        groupManager.addEventListener(listener);
        electionManager.addEventListener(listener);
    }

    public void removeEventListener(PeerEventListener listener) {
        listeners.remove(listener);
        friendManager.removeEventListener(listener);
        groupManager.removeEventListener(listener);
        electionManager.removeEventListener(listener);
    }

    /**
     * Starts the peer (RMI server, heartbeat).
     */
    public synchronized void start() throws Exception {
        if (started) {
            throw new IllegalStateException("Peer already started");
        }

        // Check username uniqueness
        List<User> existingUsers = bootstrapService.searchByUsername(localUser.username());
        if (!existingUsers.isEmpty()) {
            boolean collision = existingUsers.stream()
                    .anyMatch(u -> !u.userId().equals(localUser.userId()));

            if (collision) {
                throw new IllegalStateException("Username '" + localUser.username() + "' is already taken.");
            }
        }

        rmiServer.start(this); // We are the service
        heartbeatThread.start();
        bootstrapService.register(localUser);
        gossipManager.start();

        started = true;
        notifyLog("Peer started: " + localUser.username());
    }

    /**
     * Stops the peer and cleans up resources.
     */
    public synchronized void stop() throws Exception {
        if (!started) {
            return;
        }

        electionManager.stop();
        gossipManager.stop();
        heartbeatSender.stop();
        heartbeatThread.interrupt();

        if (bootstrapService != null) {
            try {
                bootstrapService.unregister(localUser.userId());
            } catch (Exception e) {
                // Ignore
            }
        }

        rmiServer.stop();
        started = false;
        notifyLog("Peer stopped");
    }

    // ========== Client API ==========

    public List<User> searchUsers(String username) throws Exception {
        return bootstrapService.searchByUsername(username);
    }

    public void sendFriendRequest(String username) throws Exception {
        List<User> users = bootstrapService.searchByUsername(username);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        friendManager.sendFriendRequest(users.getFirst());
    }

    public void acceptFriendRequest(String username) throws Exception {
        friendManager.acceptFriendRequest(username);
    }

    public void rejectFriendRequest(String username) throws Exception {
        friendManager.rejectFriendRequest(username);
    }

    public void sendMessage(String username, String content) throws Exception {
        User friend = friendManager.getFriendByUsername(username);
        if (friend == null) {
            throw new IllegalArgumentException("Not a friend: " + username);
        }
        messageHandler.sendMessage(friend, content);
    }

    public List<User> getFriends() {
        return friendManager.getFriends();
    }

    public List<User> getPendingRequests() {
        return friendManager.getPendingRequests();
    }

    public User getLocalUser() {
        return localUser;
    }

    public Group createGroup(String name, List<String> friendUsernames) throws Exception {
        // Deduplicate usernames
        List<String> uniqueUsernames = friendUsernames.stream()
                .distinct()
                .toList();

        List<User> friends = new ArrayList<>();
        for (String username : uniqueUsernames) {
            User friend = friendManager.getFriendByUsername(username);
            if (friend == null) {
                throw new IllegalArgumentException("Not a friend: " + username);
            }
            friends.add(friend);
        }
        friends.add(localUser);
        return groupManager.createGroup(name, friends);
    }

    /**
     * Accept a group invitation.
     */
    public void acceptGroupInvitation(String groupId) throws Exception {
        groupManager.acceptGroupInvitation(groupId);
    }

    /**
     * Reject a group invitation.
     */
    public void rejectGroupInvitation(String groupId) throws Exception {
        groupManager.rejectGroupInvitation(groupId);
    }

    /**
     * Get all pending group invitations.
     */
    public List<GroupInvitationMessage> getPendingGroupInvitations() {
        return groupManager.getPendingInvitations();
    }

    public void sendGroupMessage(String groupId, String content) throws Exception {
        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Not a member of group: " + groupId);
        }

        ChatMessage message = ChatMessage.createGroup(localUser, groupId, content, vectorClock);
        vectorClock.increment(localUser.userId());

        // Store message locally first
        groupManager.addMessage(groupId, message);

        // Broadcast to ALL active participants (members + leader)
        for (User member : group.activeMembers()) {
            if (member.userId().equals(localUser.userId())) {
                continue; // Skip self
            }
            try {
                Registry registry = LocateRegistry.getRegistry(member.ipAddress(), member.rmiPort());
                PeerService peerService = (PeerService) registry.lookup("PeerService");
                peerService.receiveMessage(message);
            } catch (Exception e) {
                notifyError("Failed to send to " + member.username(), e);
            }
        }
    }

    public List<Group> getGroups() {
        return groupManager.getGroups();
    }

    public Group getGroup(String groupId) {
        return groupManager.getGroup(groupId);
    }

    // ========== PeerService RMI Implementation ==========

    @Override
    public void receiveMessage(Message message) throws RemoteException {
        switch (message.getTopic()) {
            case CHAT -> handleChatMessage((ChatMessage) message);
            case GROUP_INVITATION -> handleGroupInvitation((GroupInvitationMessage) message);
            case GOSSIP -> handleGossipMessage((GossipMessage) message);
            case SYNC_REQUEST -> handleSyncRequest((SyncRequest) message);
            case FRIEND_MESSAGE -> handleFriendshipMessage((FriendMessage) message);
            case SYNC_RESPONSE -> handleSyncResponse((SyncResponse) message);
            case ELECTION -> handleElectionMessage((ElectionMessage) message);
            case GROUP_EVENT -> handleGroupEvent((GroupEventMessage) message);
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

    // ========== Message Handling Helpers ==========

    private void handleGroupEvent(GroupEventMessage message) {
        switch (message.getEvent()) {
            case CREATED -> groupManager.addFinalizedGroup(message.getGroup());
            case USER_JOINED -> groupManager.addUser(message.getGroup().groupId(), message.getAffectedUser());
            case DISSOLVED, USER_LEFT -> {}
        }
    }

    private void handleChatMessage(ChatMessage message) {
        synchronized (vectorClock) {
            VectorClock msgClock = message.getVectorClock();
            if (msgClock != null) {
                vectorClock.update(msgClock);
            }
        }
        if (message.getSubtopic() == ChatMessage.ChatSubtopic.DIRECT) {
            // Handle direct message
            messageHandler.handleIncomingChatMessage(message);
        } else {
            // GROUP message
            groupManager.addMessage(message.getGroupId(), message);

            Group group = groupManager.getGroup(message.getGroupId());
            if (group != null && message.getSenderId().equals(group.leader().userId())) {
                electionManager.recordLeaderActivity(message.getGroupId());
            }
        }

        // Notify all listeners for both DIRECT and GROUP messages
        for (PeerEventListener listener : listeners) {
            listener.onMessageReceived(message);
        }
    }

    private void handleGroupInvitation(GroupInvitationMessage message) {
        switch (message.getSubtopic()) {
            case REQUEST -> groupManager.handleInvitationRequest(message);
            case ACCEPT, REJECT -> groupManager.handleInvitationResponse(message);
        }
        synchronized (vectorClock) {
            if (message.getVectorClock() != null) {
                vectorClock.update(message.getVectorClock());
            }
        }
    }

    private void handleFriendshipMessage(FriendMessage message) {
        switch (message.getFriendMessageType()) {
            case FRIEND_REQUEST -> friendManager.handleFriendRequest(message.getSender());
            case FRIEND_ACCEPT -> friendManager.handleFriendAcceptance(message.getSender());
            case FRIEND_REJECT -> friendManager.handleFriendRejection(message.getSender());
        }
        synchronized (vectorClock) {
            if (message.getVectorClock() != null) {
                vectorClock.update(message.getVectorClock());
            }
        }
    }

    private void handleGossipMessage(GossipMessage message) {
        gossipManager.handleGossipMessage(message);
        synchronized (vectorClock) {
            if (message.getVectorClock() != null) {
                vectorClock.update(message.getVectorClock());
            }
        }
    }

    private void handleSyncRequest(SyncRequest message) {
        // Sync requests only happen within groups, so look up requester in group
        // members
        Group group = groupManager.getGroup(message.getGroupId());
        if (group == null) {
            notifyLog("Received sync request for unknown group: " + message.getGroupId());
            return;
        }

        User requester = group.activeMembers().stream()
                .filter(u -> u.userId().equals(message.getSenderId()))
                .findFirst()
                .orElse(null);

        if (requester != null) {
            consensusManager.handleSyncRequest(message.getGroupId(), message.getVectorClock(),
                    requester, message.getMessageId());
        } else {
            notifyLog("Received sync request from non-member: " + message.getSenderId());
        }

        synchronized (vectorClock) {
            if (message.getVectorClock() != null) {
                vectorClock.update(message.getVectorClock());
            }
        }
    }

    private void handleSyncResponse(SyncResponse message) {
        consensusManager.handleSyncResponse(message);
        synchronized (vectorClock) {
            if (message.getVectorClock() != null) {
                vectorClock.update(message.getVectorClock());
            }
        }
    }

    private void handleElectionMessage(ElectionMessage message) {
        switch (message.getElectionType()) {
            case PROPOSAL -> electionManager.handleElectionProposal(message);
            case VOTE -> electionManager.handleElectionVote(message);
            case RESULT -> electionManager.handleElectionResult(message);
        }
    }

    // ========== Wait Methods (Robust Synchronization) ==========

    /**
     * Waits for messages to be synced to the group using wait/notify.
     */
    public boolean waitForMessages(String groupId, int expectedMessageCount, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMs;

        synchronized (messageWaitLock) {
            while (System.currentTimeMillis() < endTime) {
                int messageCount = groupManager.getMessages(groupId).size();
                if (messageCount >= expectedMessageCount) {
                    return true;
                }

                try {
                    long waitTime = endTime - System.currentTimeMillis();
                    if (waitTime > 0) {
                        messageWaitLock.wait(waitTime);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Waits for a group to be created/received.
     */
    public boolean waitForGroup(String groupId, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMs;

        synchronized (groupWaitLock) {
            while (System.currentTimeMillis() < endTime) {
                if (groupId != null) {
                    if (groupManager.getGroup(groupId) != null) {
                        return true;
                    }
                } else {
                    if (!groupManager.getGroups().isEmpty()) {
                        return true;
                    }
                }

                try {
                    long waitTime = endTime - System.currentTimeMillis();
                    if (waitTime > 0) {
                        groupWaitLock.wait(waitTime);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Waits for election to complete and a new leader to be elected.
     */
    public boolean waitForNewLeader(String groupId, String oldLeaderId, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMs;

        synchronized (electionWaitLock) {
            while (System.currentTimeMillis() < endTime) {
                Group group = groupManager.getGroup(groupId);
                if (group != null && !group.leader().userId().equals(oldLeaderId)) {
                    return true;
                }

                try {
                    long waitTime = endTime - System.currentTimeMillis();
                    if (waitTime > 0) {
                        electionWaitLock.wait(waitTime);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    // ========== Notification Helpers ==========

    private void notifyLog(String message) {
        for (PeerEventListener listener : listeners) {
            listener.onLog(message);
        }
    }

    private void notifyError(String message, Throwable t) {
        for (PeerEventListener listener : listeners) {
            listener.onError(message, t);
        }
    }

    // ========== Internal Accessors (for testing) ==========

    public VectorClock getVectorClock() {
        return vectorClock;
    }

    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public GossipManager getGossipManager() {
        return gossipManager;
    }

    public LeaderElectionManager getElectionManager() {
        return electionManager;
    }

    public boolean isStarted() {
        return started;
    }

    // ========== Test Support ==========

    public void simulateNetworkFailure() {
        rmiServer.stop();
    }

    public void restoreNetwork() throws Exception {
        rmiServer.start(this);
    }

    // ========== Internal Listener for Synchronization ==========

    private class InternalEventListener implements PeerEventListener {
        @Override
        public void onFriendRequest(User requester) {
        }

        @Override
        public void onFriendRequestAccepted(User accepter) {
        }

        @Override
        public void onMessageReceived(Message message) {
            if (message.getTopic() == MessageTopic.CHAT && message instanceof ChatMessage chatMsg) {
                if (chatMsg.getSubtopic() == ChatMessage.ChatSubtopic.GROUP) {
                    synchronized (messageWaitLock) {
                        messageWaitLock.notifyAll();
                    }
                }
            }
        }

        @Override
        public void onGroupInvitation(GroupInvitationMessage request) {
        }

        @Override
        public void onGroupEvent(String groupId, GroupEvent eventType, String message) {
            synchronized (groupWaitLock) {
                groupWaitLock.notifyAll();
            }
        }

        @Override
        public void onLeaderElected(String groupId, String leaderId, long epoch) {
            synchronized (electionWaitLock) {
                electionWaitLock.notifyAll();
            }
        }

        @Override
        public void onError(String message, Throwable t) {
            System.err.println("Error: " + message);
        }

        @Override
        public void onLog(String message) {
        }
    }
}
