package p2p.peer;

import p2p.common.model.Group;
import p2p.common.model.GroupMessage;
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
import java.util.Optional;

/**
 * Programmatic peer controller for testing.
 * Provides API access to peer functionality without terminal UI.
 * 
 * @apiNote This class is designed for integration testing and automated scenarios.
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
    private boolean started = false;
    
    /**
     * Creates a new peer controller.
     * 
     * @param username Peer username
     * @param rmiPort RMI port for this peer
     * @param bootstrapHost Bootstrap server hostname
     * @param bootstrapPort Bootstrap server RMI port
     */
    public PeerController(String username, int rmiPort, String bootstrapHost, int bootstrapPort) throws Exception {
        // Create local user
        String localIp = InetAddress.getLocalHost().getHostAddress();
        this.localUser = User.create(username, localIp, rmiPort);
        
        // Initialize vector clock
        this.vectorClock = new VectorClock();
        this.vectorClock.increment(localUser.getUserId());
        
        // Create subsystems
        this.friendManager = new FriendManager(localUser, vectorClock);
        this.groupManager = new GroupManager(localUser, vectorClock);
        this.messageHandler = new MessageHandler(localUser, vectorClock, friendManager);
        this.gossipManager = new GossipManager(localUser, groupManager);
        this.consensusManager = new ConsensusManager(localUser, groupManager);
        
        // Create leader election manager
        LeaderElectionManager electionManager = new LeaderElectionManager(localUser, groupManager);
        this.groupManager.setElectionManager(electionManager);
        
        // Start RMI server
        PeerServiceImpl peerService = new PeerServiceImpl(localUser, vectorClock, friendManager, messageHandler, groupManager, gossipManager, consensusManager);
        peerService.setElectionManager(electionManager);
        this.rmiServer = new RMIServer(rmiPort, "PeerService");
        
        // Create heartbeat sender
        this.heartbeatSender = new HeartbeatSender(localUser, bootstrapHost, 9876);
        this.heartbeatThread = new Thread(heartbeatSender, "HeartbeatSender");
        this.heartbeatThread.setDaemon(true);
        
        // Connect to bootstrap
        Registry registry = LocateRegistry.getRegistry(bootstrapHost, bootstrapPort);
        this.bootstrapService = (BootstrapService) registry.lookup("BootstrapService");
        
        // Store election manager for lifecycle management
        this.electionManager = electionManager;
    }
    
    /**
     * Starts the peer (RMI server, heartbeat).
     */
    public void start() throws Exception {
        if (started) {
            throw new IllegalStateException("Peer already started");
        }
        
        rmiServer.start(new PeerServiceImpl(localUser, vectorClock, friendManager, messageHandler, groupManager, gossipManager, consensusManager));
        heartbeatThread.start();
        bootstrapService.register(localUser);
        gossipManager.start();
        electionManager.start();
        
        started = true;
    }
    
    /**
     * Stops the peer and cleans up resources.
     */
    public void stop() throws Exception {
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
    
    // ========== Testing API ==========
    
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
        User requester = friendManager.getPendingRequests().stream()
            .filter(u -> u.getUsername().equalsIgnoreCase(username))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No pending request from: " + username));
        
        friendManager.acceptFriendRequest(requester);
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
        Optional<Group> group = groupManager.getGroup(groupId);
        if (group.isEmpty()) {
            throw new IllegalArgumentException("Not a member of group: " + groupId);
        }

        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }

        GroupMessage message = GroupMessage.create(localUser, groupId, content, vectorClock.clone());
        groupManager.addMessage(groupId, message);

        // Broadcast to all group members
        for (User member : group.get().getMembers()) {
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
    }

    /**
     * Gets all groups.
     */
    public List<Group> getGroups() {
        return groupManager.getGroups();
    }

    /**
     * Gets the group manager.
     */
    public GroupManager getGroupManager() {
        return groupManager;
    }
}
