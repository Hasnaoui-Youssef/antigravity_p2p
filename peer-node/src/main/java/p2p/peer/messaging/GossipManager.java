package p2p.peer.messaging;

import p2p.common.model.message.GossipMessage;
import p2p.common.model.Group;
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
import java.util.Set;
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

    // Leader liveness tracking: groupId -> timestamp when we last saw/heard from
    // the leader
    private final Map<String, Long> leaderLastSeen = new ConcurrentHashMap<>();

    // Suspicion tracking: groupId -> Map<peerId, timestamp of their last report>
    private final Map<String, Map<String, Long>> suspicionReports = new ConcurrentHashMap<>();

    // Track groups for which we've already triggered failure callback
    private final Set<String> failureTriggered = ConcurrentHashMap.newKeySet();

    // Track group creation times for grace period (groupId -> creation timestamp)
    private final Map<String, Long> groupCreationTimes = new ConcurrentHashMap<>();

    // Grace period for newly created groups (10 seconds)
    private static final long GROUP_CREATION_GRACE_PERIOD_MS = 10000;

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
        // Also periodically check for leader timeouts independently
        scheduler.scheduleAtFixedRate(this::checkLeaderTimeouts, 2, 2, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    /**
     * Periodic check for leader timeouts (backup to gossip-based detection).
     * This ensures even isolated nodes can detect leader failure.
     */
    private void checkLeaderTimeouts() {
        long now = System.currentTimeMillis();
        long TIMEOUT_MS = 7000; // 7 seconds - longer than suspicion threshold

        for (Group group : groupManager.getGroups()) {
            String groupId = group.groupId();

            // Skip if we're the leader
            if (group.leader().userId().equals(localUser.userId())) {
                continue;
            }

            // Skip groups within the grace period after creation
            Long creationTime = groupCreationTimes.get(groupId);
            if (creationTime != null && (now - creationTime) < GROUP_CREATION_GRACE_PERIOD_MS) {
                continue;
            }

            long lastSeen = getLeaderLastSeen(groupId);
            if (now - lastSeen > TIMEOUT_MS) {
                // Leader timeout detected
                if (failureCallback != null && failureTriggered.add(groupId)) {
                    System.out.println("[Gossip] Local timeout detected for group " + group.name());
                    failureCallback.onLeaderSuspected(groupId);
                }
            }
        }
    }

    /**
     * Record leader activity (message received from leader).
     * Also records group creation time if this is the first activity for a group.
     */
    public void recordLeaderActivity(String groupId) {
        long now = System.currentTimeMillis();
        leaderLastSeen.put(groupId, now);
        // Track creation time for grace period - only set once
        groupCreationTimes.putIfAbsent(groupId, now);
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
                // Select random member to gossip with (including leader)
                List<String> members = group.activeMembers().stream()
                        .map(User::userId)
                        .filter(id -> !id.equals(localUser.userId())).collect(Collectors.toList());

                if (members.isEmpty())
                    continue;

                String targetId = members.get(random.nextInt(members.size()));
                User target = group.activeMembers().stream()
                        .filter(u -> u.userId().equals(targetId))
                        .findFirst()
                        .orElseThrow();

                // Build leader liveness map ONLY for groups both we and the target share
                // (i.e., only the current group being gossiped about)
                Map<String, Long> leaderLiveness = new HashMap<>();
                leaderLiveness.put(group.groupId(), getLeaderLastSeen(group.groupId()));

                // Create gossip message with leader liveness piggyback
                GossipMessage gossip = GossipMessage.create(
                        localUser,
                        group.groupId(),
                        groupManager.getLatestClock(group.groupId()),
                        leaderLiveness);

                sendGossipMessage(target, gossip);
            }
        } catch (Exception e) {
            System.err.println("[Gossip] Error in gossip loop: " + e.getMessage());
        }
    }

    private void sendGossipMessage(User target, GossipMessage gossip) {
        try {
            Registry registry = LocateRegistry.getRegistry(target.ipAddress(), target.rmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");
            peerService.receiveMessage(gossip);
        } catch (Exception e) {
            // System.err.println("[Gossip] Failed to gossip with " + target.getUsername() +
            // ": " + e.getMessage());
        }
    }

    /**
     * Handle incoming gossip message by comparing vector clocks and leader
     * liveness.
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
     * For small groups (<=4 members), also triggers on local timeout alone.
     */
    private void processLeaderLiveness(GossipMessage message, Group group) {
        String groupId = group.groupId();
        String senderId = message.getSenderId();

        // Skip if we are the leader
        if (group.leader().userId().equals(localUser.userId())) {
            return;
        }

        // Skip groups within the grace period after creation
        long now = System.currentTimeMillis();
        Long creationTime = groupCreationTimes.get(groupId);
        if (creationTime != null && (now - creationTime) < GROUP_CREATION_GRACE_PERIOD_MS) {
            return;
        }

        // Get remote peer's last seen timestamp for this group's leader
        Map<String, Long> remoteLiveness = message.getLeaderLastSeen();
        if (remoteLiveness == null || !remoteLiveness.containsKey(groupId)) {
            return;
        }

        long remoteTimestamp = remoteLiveness.get(groupId);
        long staleness = now - remoteTimestamp;

        // Suspicion threshold: 5 seconds
        long SUSPICION_THRESHOLD_MS = 5000;

        if (staleness > SUSPICION_THRESHOLD_MS) {
            // Peer reports stale leader timestamp - record suspicion
            suspicionReports.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>())
                    .put(senderId, now);

            // Check our own last seen timestamp too
            long ourLastSeen = getLeaderLastSeen(groupId);
            boolean weAlsoSuspect = (now - ourLastSeen) > SUSPICION_THRESHOLD_MS;

            System.out.println("[Gossip] Peer " + senderId.substring(0, 8) + " suspects leader, " +
                    "we also suspect: " + weAlsoSuspect);

            if (!weAlsoSuspect) {
                return; // We don't suspect the leader yet
            }

            // Check if we have enough suspicion reports
            Map<String, Long> reports = suspicionReports.get(groupId);

            // Count recent reports (within last 10 seconds)
            long recentReportCount = reports.values().stream()
                    .filter(timestamp -> now - timestamp < 10000)
                    .count();

            // For small groups (3-4 members), require at least 1 peer report + our own
            // suspicion
            // OR just our own suspicion if enough time has passed
            // For larger groups, require majority
            int totalMembers = group.members().size() + 1; // members + leader
            boolean shouldTrigger;

            if (totalMembers <= 4) {
                // Small group: trigger if we suspect AND at least one other peer suspects
                // OR if we've been suspecting for more than 2 * SUSPICION_THRESHOLD (10
                // seconds)
                long localSuspicionDuration = now - ourLastSeen;
                shouldTrigger = (recentReportCount >= 1) || (localSuspicionDuration > 2 * SUSPICION_THRESHOLD_MS);
            } else {
                // Larger group: require quorum
                int quorum = (totalMembers / 2) + 1;
                shouldTrigger = recentReportCount >= quorum - 1; // -1 because we don't count ourselves in reports
            }

            System.out.println("[Gossip] Reports: " + recentReportCount + ", totalMembers: " + totalMembers +
                    ", shouldTrigger: " + shouldTrigger);

            if (shouldTrigger && failureCallback != null) {
                // Only trigger once per group (until leader changes)
                if (failureTriggered.add(groupId)) {
                    System.out.println("[Gossip] Triggering leader failure callback for group " + groupId);
                    failureCallback.onLeaderSuspected(groupId);
                }
                // Don't clear suspicion reports - let them accumulate for other nodes
            }
        }
    }

    /**
     * Clear failure tracking when a new leader is elected or group is dissolved.
     */
    public void clearFailureTracking(String groupId) {
        failureTriggered.remove(groupId);
        suspicionReports.remove(groupId);
    }
}
