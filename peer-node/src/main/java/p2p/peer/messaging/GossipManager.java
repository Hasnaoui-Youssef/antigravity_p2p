package p2p.peer.messaging;

import p2p.common.model.message.GossipMessage;
import p2p.common.model.Group;
import p2p.common.model.message.Message;
import p2p.common.model.message.SyncRequest;
import p2p.common.model.User;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.consensus.ConsensusManager;
import p2p.peer.groups.GroupManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implements Cassandra-style gossip protocol with digest comparison.
 * Includes gossip-based leader failure detection.
 */
public class GossipManager {

    private final User localUser;
    private final GroupManager groupManager;
    private ConsensusManager consensusManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    // Leader liveness tracking: groupId -> timestamp when we last saw/heard from the leader
    private final Map<String, Long> leaderLastSeen = new ConcurrentHashMap<>();
    
    // Suspicion tracking: groupId -> Map<peerId, timestamp of their last report>
    private final Map<String, Map<String, Long>> suspicionReports = new ConcurrentHashMap<>();
    
    // Callback for leader failure detection
    private LeaderFailureCallback failureCallback;

    public GossipManager(User localUser, GroupManager groupManager) {
        this.localUser = localUser;
        this.groupManager = groupManager;
    }
    
    public void setConsensusManager(ConsensusManager consensusManager) {
        this.consensusManager = consensusManager;
    }
    
    public void setLeaderFailureCallback(LeaderFailureCallback callback) {
        this.failureCallback = callback;
    }
    
    /**
     * Callback interface for leader failure detection.
     */
    public interface LeaderFailureCallback {
        void onLeaderSuspected(String groupId);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::gossip, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }
    
    /**
     * Record leader activity (message received from leader).
     */
    public void recordLeaderActivity(String groupId) {
        leaderLastSeen.put(groupId, System.currentTimeMillis());
    }
    
    /**
     * Get the last time we saw the leader for a group.
     */
    public long getLeaderLastSeen(String groupId) {
        return leaderLastSeen.getOrDefault(groupId, System.currentTimeMillis());
    }

    /**
     * Periodic gossip round.
     */
    private void gossip() {
        try {
            List<Group> groups = groupManager.getGroups();
            for (Group group : groups) {
                // Select random member to gossip with
                List<String> members = new ArrayList<>(group.getMembers().stream()
                    .map(User::getUserId)
                    .filter(id -> !id.equals(localUser.getUserId()))
                    .collect(Collectors.toList()));
                
                if (members.isEmpty()) continue;
                
                String targetId = members.get(random.nextInt(members.size()));
                User target = group.getMembers().stream()
                    .filter(u -> u.getUserId().equals(targetId))
                    .findFirst()
                    .orElseThrow();
                
                // Build leader liveness map for all groups
                Map<String, Long> leaderLiveness = new HashMap<>();
                for (Group g : groups) {
                    leaderLiveness.put(g.getGroupId(), getLeaderLastSeen(g.getGroupId()));
                }
                
                // Create gossip message with leader liveness piggyback
                GossipMessage gossip = GossipMessage.create(
                    localUser, 
                    group.getGroupId(), 
                    groupManager.getLatestClock(group.getGroupId()),
                    leaderLiveness
                );
                
                sendGossipMessage(target, gossip);
            }
        } catch (Exception e) {
            System.err.println("[Gossip] Error in gossip loop: " + e.getMessage());
        }
    }

    private void sendGossipMessage(User target, GossipMessage gossip) {
        try {
            Registry registry = LocateRegistry.getRegistry(target.getIpAddress(), target.getRmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");
            peerService.receiveMessage(gossip);
        } catch (Exception e) {
            // System.err.println("[Gossip] Failed to gossip with " + target.getUsername() + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle incoming gossip message by comparing vector clocks and leader liveness.
     */
    public void handleGossipMessage(GossipMessage message) {
        String groupId = message.getGroupId();
        VectorClock remoteClock = message.getVectorClock();
        
        // Check if we have this group
        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            return; // We're not in this group
        }
        
        VectorClock localClock = groupManager.getLatestClock(groupId);
        
        // Compare clocks to see if we are missing messages
        // If remoteClock > localClock, we are behind
        if (localClock.happensBefore(remoteClock)) {
            // We are behind, request sync
            if (consensusManager != null) {
                consensusManager.initiateSync(groupId, localClock);
            }
        }
        
        // Process leader liveness information
        processLeaderLiveness(message, group);
    }
    
    /**
     * Process leader liveness reports from gossip messages.
     * Uses Phi Accrual-style failure detection with suspicion accumulation.
     */
    private void processLeaderLiveness(GossipMessage message, Group group) {
        String groupId = group.getGroupId();
        String senderId = message.getSenderId();
        
        // Skip if we are the leader
        if (group.getLeaderId().equals(localUser.getUserId())) {
            return;
        }
        
        // Get remote peer's last seen timestamp for this group's leader
        Map<String, Long> remoteLiveness = message.getLeaderLastSeen();
        if (remoteLiveness == null || !remoteLiveness.containsKey(groupId)) {
            return;
        }
        
        long remoteTimestamp = remoteLiveness.get(groupId);
        long now = System.currentTimeMillis();
        long staleness = now - remoteTimestamp;
        
        // Suspicion threshold: 5 seconds
        long SUSPICION_THRESHOLD_MS = 5000;
        
        if (staleness > SUSPICION_THRESHOLD_MS) {
            // Peer reports stale leader timestamp - record suspicion
            suspicionReports.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>())
                .put(senderId, now);
            
            // Check if we have enough suspicion reports (quorum)
            Map<String, Long> reports = suspicionReports.get(groupId);
            
            // Count recent reports (within last 10 seconds)
            long recentReportCount = reports.values().stream()
                .filter(timestamp -> now - timestamp < 10000)
                .count();
            
            // Calculate group size (leader + members)
            int groupSize = group.getMembers().size() + 1;
            int quorum = (groupSize / 2) + 1;
            
            // If majority of peers suspect leader failure, trigger election
            if (recentReportCount >= quorum - 1) { // -1 because we don't count ourselves yet
                // Add our own suspicion
                long ourLastSeen = getLeaderLastSeen(groupId);
                if (now - ourLastSeen > SUSPICION_THRESHOLD_MS) {
                    if (failureCallback != null) {
                        failureCallback.onLeaderSuspected(groupId);
                    }
                    // Clear suspicion reports after triggering
                    suspicionReports.remove(groupId);
                }
            }
        }
    }
}
