package p2p.peer;

import p2p.common.model.Group;
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
        this.vectorClock.increment(localUser.getUserId());

        this.friendManager = new FriendManager(localUser, vectorClock);
        this.groupManager = new GroupManager(localUser, vectorClock, friendManager);
        this.messageHandler = new MessageHandler(localUser, vectorClock, friendManager);
        this.gossipManager = new GossipManager(localUser, groupManager);
        this.consensusManager = new ConsensusManager(localUser, groupManager);
        this.gossipManager.setConsensusManager(consensusManager);

        this.electionManager = new LeaderElectionManager(localUser, groupManager);
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
        List<User> existingUsers = bootstrapService.searchByUsername(localUser.getUsername());
        if (!existingUsers.isEmpty()) {
            // Check if it's not us (e.g. stale registration) - strictly speaking we should
            // fail if ANYONE has this name
            // But for robustness, if the IP/Port matches, maybe it's a restart.
            // However, the requirement is strict uniqueness.
            boolean collision = existingUsers.stream()
                    .anyMatch(u -> !u.getUserId().equals(localUser.getUserId()));

            if (collision) {
                throw new IllegalStateException("Username '" + localUser.getUsername() + "' is already taken.");
            }
        }

        rmiServer.start(this); // We are the service
        heartbeatThread.start();
        bootstrapService.register(localUser);
        gossipManager.start();
        electionManager.start();

        started = true;
        notifyLog("Peer started: " + localUser.getUsername());
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
                bootstrapService.unregister(localUser.getUserId());
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
        friendManager.sendFriendRequest(users.get(0));
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
                .collect(java.util.stream.Collectors.toList());

        List<User> friends = new ArrayList<>();
        for (String username : uniqueUsernames) {
            User friend = friendManager.getFriendByUsername(username);
            if (friend == null) {
                throw new IllegalArgumentException("Not a friend: " + username);
            }
            friends.add(friend);
        }
        return groupManager.createGroup(name, friends);
    }

    public void sendGroupMessage(String groupId, String content) throws Exception {
        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Not a member of group: " + groupId);
        }

        ChatMessage message = ChatMessage.createGroup(localUser, groupId, content, vectorClock);
        vectorClock.increment(localUser.getUserId());

        // Store message locally first
        groupManager.addMessage(groupId, message);

        // Broadcast to ALL group participants (members + leader)
        for (User member : group.getMembers()) {
            if (member.getUserId().equals(localUser.getUserId())) {
                continue; // Skip self
            }
            try {
                Registry registry = LocateRegistry.getRegistry(member.getIpAddress(), member.getRmiPort());
                PeerService peerService = (PeerService) registry.lookup("PeerService");
                peerService.receiveMessage(message);
            } catch (Exception e) {
                notifyError("Failed to send to " + member.getUsername(), e);
            }
        }

        // Also send to leader if we're not the leader
        if (!group.getLeaderId().equals(localUser.getUserId())) {
            try {
                User leader = friendManager.getFriends().stream()
                        .filter(f -> f.getUserId().equals(group.getLeaderId()))
                        .findFirst()
                        .orElse(null);

                if (leader != null) {
                    Registry registry = LocateRegistry.getRegistry(leader.getIpAddress(), leader.getRmiPort());
                    PeerService peerService = (PeerService) registry.lookup("PeerService");
                    peerService.receiveMessage(message);
                }
            } catch (Exception e) {
                notifyError("Failed to send to leader", e);
            }
        }
    }

    public List<Group> getGroups() {
        return groupManager.getGroups();
    }

    public Group getGroup(String groupId) {
        return groupManager.getGroup(groupId);
    }

    public void setInvitationHandler(p2p.peer.groups.InvitationHandler handler) {
        groupManager.setInvitationHandler(handler);
    }

    // ========== PeerService RMI Implementation ==========

    // Removed receiveFriendRequest and acceptFriendRequest as they are now messages

    @Override
    public void receiveMessage(Message message) throws RemoteException {
        System.out.println("[DEBUG] " + localUser.getUsername() + " received message: " + message.getTopic() + " from "
                + message.getSenderId());
        switch (message.getTopic()) {
            // New unified message types
            case CHAT -> handleChatMessage((ChatMessage) message);
            case GROUP_INVITATION -> handleGroupInvitation((GroupInvitationMessage) message);
            
            // Legacy message types (deprecated but kept for backwards compatibility)
            case DIRECT -> handleDirectMessage((DirectMessage) message);
            case GROUP -> handleGroupMessage((GroupMessage) message);
            case GOSSIP -> handleGossipMessage((GossipMessage) message);
            case SYNC_REQUEST -> handleSyncRequest((SyncRequest) message);
            case FRIEND_MESSAGE -> handleFriendshipMessage((FriendMessage) message);
            case SYNC_RESPONSE -> handleSyncResponse((SyncResponse) message);
            case ELECTION -> handleElectionMessage((ElectionMessage) message);
            case INVITATION_REQUEST -> handleInvitationRequest((GroupInvitationRequest) message);
            case INVITATION_RESPONSE -> handleInvitationResponse((GroupInvitationResponse) message);
            case GROUP_REJECT -> {
                if (message instanceof GroupRejectMessage reject) {
                    groupManager.handleGroupReject(reject);
                }
            }
        }

        // Notify listeners of message receipt
        for (PeerEventListener listener : listeners) {
            listener.onMessageReceived(message);
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
        groupManager.addFinalizedGroup(group);
    }

    // ========== Message Handling Helpers ==========

    private void handleChatMessage(ChatMessage message) {
        synchronized (vectorClock) {
            VectorClock msgClock = message.getVectorClock();
            if (msgClock != null) {
                vectorClock.update(msgClock);
            }
        }
        
        if (message.getSubtopic() == ChatSubtopic.DIRECT) {
            messageHandler.handleIncomingChatMessage(message);
        } else {
            // GROUP message
            groupManager.addMessage(message.getGroupId(), message);
            
            Group group = groupManager.getGroup(message.getGroupId());
            if (group != null && message.getSenderId().equals(group.getLeaderId())) {
                electionManager.recordLeaderActivity(message.getGroupId());
            }
        }
    }

    private void handleGroupInvitation(GroupInvitationMessage message) {
        System.out.println("[DEBUG] " + localUser.getUsername() + " handling group invitation: " 
                + message.getSubtopic() + " from " + message.getSenderId());
        
        switch (message.getSubtopic()) {
            case REQUEST -> groupManager.handleInvitationRequest(message);
            case ACCEPT, REJECT -> groupManager.handleInvitationResponse(message);
        }
    }

    private void handleFriendshipMessage(FriendMessage message) {
        switch (message.getFriendMessageType()) {
            case FRIEND_REQUEST -> friendManager.handleFriendRequest(message.getSender());
            case FRIEND_ACCEPT -> friendManager.handleFriendAcceptance(message.getSender());
            case FRIEND_REJECT -> friendManager.handleFriendRejection(message.getSender());
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
        groupManager.addMessage(message.getGroupId(), message);

        Group group = groupManager.getGroup(message.getGroupId());
        if (group != null && message.getSenderId().equals(group.getLeaderId())) {
            electionManager.recordLeaderActivity(message.getGroupId());
        }
    }

    private void handleGossipMessage(GossipMessage message) {
        gossipManager.handleGossipMessage(message);
    }

    private void handleSyncRequest(SyncRequest message) {
        User requester = friendManager.getFriends().stream()
                .filter(u -> u.getUserId().equals(message.getSenderId()))
                .findFirst()
                .orElse(null);

        if (requester != null) {
            consensusManager.handleSyncRequest(message.getGroupId(), message.getLastKnownState(), requester,
                    message.getMessageId());
        }
    }

    private void handleSyncResponse(SyncResponse message) {
        consensusManager.handleSyncResponse(message);
    }

    private void handleElectionMessage(ElectionMessage message) {
        switch (message.getElectionType()) {
            case PROPOSAL -> electionManager.handleElectionProposal(message);
            case VOTE -> electionManager.handleElectionVote(message);
            case RESULT -> electionManager.handleElectionResult(message);
        }
    }

    private void handleInvitationRequest(GroupInvitationRequest message) {
        System.out.println(
                "[DEBUG] " + localUser.getUsername() + " handling invitation request from " + message.getSenderId());
        groupManager.handleInvitationRequest(message);
    }

    private void handleInvitationResponse(GroupInvitationResponse message) {
        System.out.println("[DEBUG] " + localUser.getUsername() + " handling invitation response from "
                + message.getSenderId() + " status=" + message.getStatus());
        groupManager.handleInvitationResponse(message);
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
                if (group != null && !group.getLeaderId().equals(oldLeaderId)) {
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
            if (message.getTopic() == MessageTopic.GROUP) {
                synchronized (messageWaitLock) {
                    messageWaitLock.notifyAll();
                }
            }
        }

        @Override
        public void onGroupInvitation(GroupInvitationRequest request) {
        }

        @Override
        public void onGroupEvent(String groupId, String eventType, String message) {
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
        }

        @Override
        public void onLog(String message) {
        }
    }
}
