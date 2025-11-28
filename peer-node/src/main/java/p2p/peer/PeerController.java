package p2p.peer;

import p2p.common.model.Group;
import p2p.common.model.message.GroupMessage;
import p2p.common.model.User;
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
import p2p.peer.network.PeerServiceImpl;
import p2p.peer.network.RMIServer;

import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

/**
 * Programmatic peer controller for testing.
 * Provides API access to peer functionality without terminal UI.
 * 
 * @apiNote This class is designed for integration testing and automated
 *          scenarios.
 */
public class PeerController {

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

        LeaderElectionManager electionManager = new LeaderElectionManager(localUser, groupManager);
        electionManager.setGossipManager(gossipManager);
        this.groupManager.setElectionManager(electionManager);

        peerService = new PeerServiceImpl(localUser, vectorClock, friendManager, messageHandler,
                groupManager, gossipManager, consensusManager);
        peerService.setElectionManager(electionManager);
        this.rmiServer = new RMIServer(rmiPort, "PeerService");

        this.heartbeatSender = new HeartbeatSender(localUser, bootstrapHost, udpPort);
        this.heartbeatThread = new Thread(heartbeatSender, "HeartbeatSender");
        this.heartbeatThread.setDaemon(true);

        Registry registry = LocateRegistry.getRegistry(bootstrapHost, bootstrapPort);
        this.bootstrapService = (BootstrapService) registry.lookup("BootstrapService");

        this.electionManager = electionManager;
    }

    private PeerServiceImpl peerService;

    /**
     * Starts the peer (RMI server, heartbeat).
     */
    public synchronized void start() throws Exception {
        if (started) {
            throw new IllegalStateException("Peer already started");
        }

        rmiServer.start(peerService); // Use the pre-configured instance
        heartbeatThread.start();
        bootstrapService.register(localUser);
        gossipManager.start();
        electionManager.start();

        started = true;
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
    }

    public void login() throws Exception {
        bootstrapService.register(localUser);
    }

    public void logout() throws Exception {
        bootstrapService.unregister(localUser.getUserId());
    }

    /**
     * Searches for users by username.
     */
    public List<User> searchUsers(String username) throws Exception {
        return bootstrapService.searchByUsername(username);
    }

    /**
     * Sends a friend request to another user.
     */
    public void sendFriendRequest(String username) throws Exception {
        List<User> users = bootstrapService.searchByUsername(username);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        friendManager.sendFriendRequest(users.get(0));
    }

    /**
     * Accepts a pending friend request.
     */
    public void acceptFriendRequest(String username) throws Exception {
        friendManager.acceptFriendRequest(username);
    }

    /**
     * Rejects a pending friend request.
     */
    public void rejectFriendRequest(String username) throws Exception {
        friendManager.rejectFriendRequest(username);
    }

    /**
     * Sends a message to a friend.
     */
    public void sendMessage(String username, String content) throws Exception {
        User friend = friendManager.getFriendByUsername(username);
        if (friend == null) {
            throw new IllegalArgumentException("Not a friend: " + username);
        }
        messageHandler.sendMessage(friend, content);
    }

    /**
     * Gets list of friends.
     */
    public List<User> getFriends() {
        return friendManager.getFriends();
    }

    /**
     * Gets list of pending friend requests.
     */
    public List<User> getPendingRequests() {
        return friendManager.getPendingRequests();
    }

    /**
     * Gets the local user.
     */
    public User getLocalUser() {
        return localUser;
    }

    /**
     * Gets the vector clock (for verification in tests).
     */
    public VectorClock getVectorClock() {
        return vectorClock;
    }

    /**
     * Gets the message handler (for checking received messages in tests).
     */
    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    /**
     * Checks if peer is started.
     */
    public boolean isStarted() {
        return started;
    }

    // ========== Group Management API ==========

    /**
     * Creates a new group with the given name and friends.
     */
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

    /**
     * Sends a message to a group.
     */
    public void sendGroupMessage(String groupId, String content) throws Exception {
        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Not a member of group: " + groupId);
        }

        GroupMessage message = GroupMessage.create(localUser, groupId, content, vectorClock);
        vectorClock.increment(localUser.getUserId());

        // Store message locally first
        groupManager.addMessage(groupId, message);

        // Broadcast to ALL group participants (members + leader)
        // Members list doesn't include leader, so we need to handle both
        for (User member : group.getMembers()) {
            if (member.getUserId().equals(localUser.getUserId())) {
                continue; // Skip self
            }

            try {
                Registry registry = LocateRegistry.getRegistry(member.getIpAddress(), member.getRmiPort());
                PeerService peerService = (PeerService) registry.lookup("PeerService");
                peerService.receiveMessage(message);
            } catch (Exception e) {
                System.err.println("[Group] Failed to send to " + member.getUsername() + ": " + e.getMessage());
            }
        }

        // Also send to leader if we're not the leader
        if (!group.getLeaderId().equals(localUser.getUserId())) {
            try {
                // Find leader in friends or members
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
                System.err.println("[Group] Failed to send to leader: " + e.getMessage());
            }
        }
    }

    /**
     * Gets all groups.
     */
    public List<Group> getGroups() {
        return groupManager.getGroups();
    }

    /**
     * Get a specific group by ID.
     */
    public Group getGroup(String groupId) {
        return groupManager.getGroup(groupId);
    }

    /**
     * Gets the group manager.
     */
    public GroupManager getGroupManager() {
        return groupManager;
    }

    /**
     * Set the invitation handler for controlling accept/reject decisions.
     * Used by tests to programmatically control invitation responses.
     */
    public void setInvitationHandler(p2p.peer.groups.InvitationHandler handler) {
        groupManager.setInvitationHandler(handler);
    }

    // ========== Network Simulation for Testing ==========

    /**
     * Simulates network failure by stopping the RMI server.
     * The peer will be unable to receive messages.
     */
    public void simulateNetworkFailure() {
        rmiServer.stop();
    }

    /**
     * Restores network connectivity by restarting the RMI server.
     */
    public void restoreNetwork() throws Exception {
        PeerServiceImpl peerService = new PeerServiceImpl(localUser, vectorClock, friendManager, messageHandler,
                groupManager, gossipManager, consensusManager);
        peerService.setElectionManager(electionManager);
        rmiServer.start(peerService);
    }

    /**
     * Sets a callback for sync completion events.
     * Used by tests to wait for sync operations to complete.
     */
    public void setSyncCallback(ConsensusManager.SyncCallback callback) {
        consensusManager.setSyncCallback(callback);
    }

    /**
     * Gets the consensus manager for testing purposes.
     */
    public ConsensusManager getConsensusManager() {
        return consensusManager;
    }

    /**
     * Waits for messages to be synced to the group, with polling and timeout.
     * Returns true if the expected message count was reached, false if timeout.
     *
     * @param groupId              The group ID to check
     * @param expectedMessageCount The minimum number of messages to wait for
     * @param timeoutMs            Maximum time to wait in milliseconds
     * @param pollIntervalMs       Time between checks in milliseconds
     * @return true if messages synced, false if timeout
     */
    public boolean waitForMessages(String groupId, int expectedMessageCount, long timeoutMs, long pollIntervalMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            int messageCount = groupManager.getMessages(groupId).size();
            if (messageCount >= expectedMessageCount) {
                return true;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Waits for a group to be created/received, with polling and timeout.
     * Returns true if the group exists, false if timeout.
     *
     * @param groupId        The group ID to wait for (or null to wait for any
     *                       group)
     * @param timeoutMs      Maximum time to wait in milliseconds
     * @param pollIntervalMs Time between checks in milliseconds
     * @return true if group found, false if timeout
     */
    public boolean waitForGroup(String groupId, long timeoutMs, long pollIntervalMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
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
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Waits for election to complete and a new leader to be elected.
     * Returns true if new leader differs from old leader, false if timeout.
     *
     * @param groupId        The group ID
     * @param oldLeaderId    The previous leader's ID
     * @param timeoutMs      Maximum time to wait in milliseconds
     * @param pollIntervalMs Time between checks in milliseconds
     * @return true if new leader elected, false if timeout
     */
    public boolean waitForNewLeader(String groupId, String oldLeaderId, long timeoutMs, long pollIntervalMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            Group group = groupManager.getGroup(groupId);
            if (group != null && !group.getLeaderId().equals(oldLeaderId)) {
                return true;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
