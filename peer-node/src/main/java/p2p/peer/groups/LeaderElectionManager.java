package p2p.peer.groups;

import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.common.model.*;
import p2p.common.model.message.*;
import p2p.common.model.message.ElectionMessage;
import p2p.common.rmi.PeerService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages leader election for groups using ZooKeeper-style protocol.
 */
public class LeaderElectionManager {
    
    private final User localUser;
    private final GroupManager groupManager;
    
    // Track last heartbeat from leaders: groupId -> timestamp
    private final Map<String, Long> lastLeaderHeartbeat = new ConcurrentHashMap<>();
    
    // Track ongoing elections: groupId -> ElectionState
    private final Map<String, ElectionState> ongoingElections = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private static final long HEARTBEAT_TIMEOUT_MS = 5000; // 5 seconds
    private static final long ELECTION_TIMEOUT_MS = 3000; // 3 seconds
    
    public LeaderElectionManager(User localUser, GroupManager groupManager) {
        this.localUser = localUser;
        this.groupManager = groupManager;
    }
    
    public void start() {
        // Check for leader timeouts every 2 seconds
        scheduler.scheduleAtFixedRate(this::checkLeaderTimeouts, 2, 2, TimeUnit.SECONDS);
    }
    
    public void stop() {
        scheduler.shutdown();
        executor.shutdown();
    }
    
    /**
     * Record heartbeat from group leader.
     */
    public void recordLeaderHeartbeat(String groupId) {
        lastLeaderHeartbeat.put(groupId, System.currentTimeMillis());
    }
    
    /**
     * Check for leader timeouts and trigger elections.
     */
    private void checkLeaderTimeouts() {
        long now = System.currentTimeMillis();
        
        for (Group group : groupManager.getGroups()) {
            String groupId = group.getGroupId();
            
            // Skip if we're not tracking this group's leader
            if (!lastLeaderHeartbeat.containsKey(groupId)) {
                lastLeaderHeartbeat.put(groupId, now);
                continue;
            }
            
            // Skip if we're the leader
            if (group.getLeaderId().equals(localUser.getUserId())) {
                continue;
            }
            
            long lastHeartbeat = lastLeaderHeartbeat.get(groupId);
            if (now - lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                System.out.println("[Election] Leader timeout for group " + group.getName());
                initiateElection(group);
                lastLeaderHeartbeat.put(groupId, now); // Reset to avoid repeated elections
            }
        }
    }
    
    /**
     * Initiate a new election for the group.
     */
    public void initiateElection(Group group) {
        String groupId = group.getGroupId();
        
        // Check if election already ongoing
        if (ongoingElections.containsKey(groupId)) {
            return;
        }
        
        long newEpoch = group.getEpoch() + 1;
        ElectionState state = new ElectionState(groupId, newEpoch, localUser.getUserId());
        ongoingElections.put(groupId, state);
        
        System.out.println("[Election] Starting election for group " + group.getName() + " (epoch " + newEpoch + ")");
        
        // Broadcast proposal
        ElectionMessage proposal = ElectionMessage.create(
            localUser.getUserId(), groupId, 
            ElectionMessage.ElectionType.PROPOSAL, 
            localUser.getUserId(), newEpoch
        );
        
        broadcastToGroup(group, proposal);
        
        // Schedule election timeout
        scheduler.schedule(() -> finalizeElection(groupId), ELECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Handle incoming election proposal.
     */
    public void handleElectionProposal(ElectionMessage message) {
        Group group = groupManager.getGroup(message.getGroupId());
        if (group == null) {
            return;
        }
        
        // Vote for the candidate (in this simple version, vote for first proposal)
        ElectionState state = ongoingElections.get(message.getGroupId());
        if (state == null) {
            state = new ElectionState(message.getGroupId(), message.getEpoch(), message.getCandidateId());
            ongoingElections.put(message.getGroupId(), state);
        }
        
        // Send vote
        ElectionMessage vote = ElectionMessage.create(
            localUser.getUserId(), message.getGroupId(),
            ElectionMessage.ElectionType.VOTE,
            message.getCandidateId(), message.getEpoch()
        );
        
        broadcastToGroup(group, vote);
    }
    
    /**
     * Handle incoming election vote.
     */
    public void handleElectionVote(ElectionMessage message) {
        ElectionState state = ongoingElections.get(message.getGroupId());
        if (state == null || state.epoch != message.getEpoch()) {
            return;
        }
        
        state.recordVote(message.getCandidateId());
    }
    
    /**
     * Handle election result announcement.
     */
    public void handleElectionResult(ElectionMessage message) {
        Group group = groupManager.getGroup(message.getGroupId());
        if (group == null) {
            return;
        }
        Group updatedGroup = group.withNewLeader(message.getCandidateId(), message.getEpoch());
        groupManager.updateGroup(updatedGroup);
        
        ongoingElections.remove(message.getGroupId());
        lastLeaderHeartbeat.put(message.getGroupId(), System.currentTimeMillis());
        
        System.out.println("[Election] New leader elected for group " + group.getName() + 
            " - epoch " + message.getEpoch());
    }
    
    /**
     * Finalize election and announce result.
     */
    private void finalizeElection(String groupId) {
        ElectionState state = ongoingElections.get(groupId);
        if (state == null) {
            return;
        }
        
        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            return;
        }
        
        // Determine winner (candidate with most votes)
        String winner = state.getWinner();
        
        // Update group with new leader
        Group updatedGroup = group.withNewLeader(winner, state.epoch);
        groupManager.updateGroup(updatedGroup);
        
        // Broadcast result
        ElectionMessage result = ElectionMessage.create(
            localUser.getUserId(), groupId,
            ElectionMessage.ElectionType.RESULT,
            winner, state.epoch
        );
        
        broadcastToGroup(updatedGroup, result);
        
        ongoingElections.remove(groupId);
        lastLeaderHeartbeat.put(groupId, System.currentTimeMillis());
        
        System.out.println("[Election] Election complete for group " + group.getName() + 
            " - new leader: " + winner + " (epoch " + state.epoch + ")");
    }
    
    /**
     * Broadcast message to all group members.
     */
    private void broadcastToGroup(Group group, ElectionMessage message) {
        for (User member : group.getMembers()) {
            if (member.getUserId().equals(localUser.getUserId())) {
                continue;
            }
            
            executor.submit(() -> {
                try {
                    Registry registry = LocateRegistry.getRegistry(member.getIpAddress(), member.getRmiPort());
                    PeerService peerService = (PeerService) registry.lookup("PeerService");
                    peerService.receiveMessage(message);
                } catch (Exception e) {
                    System.err.println("[Election] Failed to send to " + member.getUsername() + ": " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * State tracking for an ongoing election.
     */
    private static class ElectionState {
        final String groupId;
        final long epoch;
        final Map<String, Integer> votes = new ConcurrentHashMap<>();
        
        ElectionState(String groupId, long epoch, String initialCandidate) {
            this.groupId = groupId;
            this.epoch = epoch;
            votes.put(initialCandidate, 1);
        }
        
        void recordVote(String candidateId) {
            votes.merge(candidateId, 1, Integer::sum);
        }
        
        String getWinner() {
            return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }
    }
}
