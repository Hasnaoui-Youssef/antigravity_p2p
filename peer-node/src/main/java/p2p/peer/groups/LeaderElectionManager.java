package p2p.peer.groups;

import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.common.model.message.ElectionMessage;
import p2p.common.rmi.PeerService;
import p2p.peer.PeerEventListener;
import p2p.peer.messaging.GossipManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages leader election for groups using gossip-based failure detection
 * and deterministic leader selection with epoch-based coordination.
 */
public class LeaderElectionManager {

    private final User localUser;
    private final GroupManager groupManager;
    private GossipManager gossipManager;

    // Track ongoing elections: groupId -> ElectionState
    private final Map<String, ElectionState> ongoingElections = new ConcurrentHashMap<>();

    // Track which epochs we've already voted in: groupId -> Set<epoch>
    private final Map<String, Set<Long>> votedEpochs = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final long ELECTION_TIMEOUT_MS = 3000; // 3 seconds

    private final List<PeerEventListener> listeners = new CopyOnWriteArrayList<>();

    public LeaderElectionManager(User localUser, GroupManager groupManager) {
        this.localUser = localUser;
        this.groupManager = groupManager;
    }

    public void addEventListener(PeerEventListener listener) {
        listeners.add(listener);
    }

    public void removeEventListener(PeerEventListener listener) {
        listeners.remove(listener);
    }

    public void setGossipManager(GossipManager gossipManager) {
        this.gossipManager = gossipManager;
        // Register callback for leader failure detection
        gossipManager.setLeaderFailureCallback(this::initiateElectionForGroup);
    }

    public void start() {
        // No periodic heartbeat checking - we rely on gossip-based detection
    }

    public void stop() {
        scheduler.shutdown();
        executor.shutdown();
    }

    /**
     * Record activity from group leader (for gossip propagation).
     * If we have an ongoing election and the leader shows activity, cancel the
     * election.
     */
    public void recordLeaderActivity(String groupId) {
        if (gossipManager != null) {
            gossipManager.recordLeaderActivity(groupId);
        }

        // If there's an ongoing election and leader is now active, cancel election
        ElectionState state = ongoingElections.get(groupId);
        if (state != null) {
            Group group = groupManager.getGroup(groupId);
            if (group != null) {
                // Leader is still alive - cancel the election
                notifyLog("Leader activity detected for group " + group.getName() +
                        " - cancelling election (epoch " + state.epoch + ")");
                ongoingElections.remove(groupId);

                // Clear the failure tracking so we don't re-trigger immediately
                if (gossipManager != null) {
                    gossipManager.clearFailureTracking(groupId);
                }
            }
        }
    }

    /**
     * Initiate election when gossip detects leader failure.
     */
    private void initiateElectionForGroup(String groupId) {
        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            return;
        }

        // Check if group has fallen below minimum size
        // When leader fails, only non-leader members remain
        int remainingMembers = group.getMembers().size(); // Excludes the failed leader
        if (remainingMembers <= 2) {
            // Need at least 3 members after selecting new leader (2 non-leader + 1 new
            // leader)
            // With only 2 remaining, we'd have 1 leader + 1 member = 2 total (below minimum
            // of 3)
            notifyLog("Group " + group.getName() + " has only " +
                    remainingMembers + " remaining members after leader failure. Auto-dissolving...");
            groupManager.dissolveGroup(groupId);
            if (gossipManager != null) {
                gossipManager.clearFailureTracking(groupId);
            }
            return;
        }

        initiateElection(group);
    }

    /**
     * Initiate a new election for the group with deterministic candidate selection.
     */
    public void initiateElection(Group group) {
        String groupId = group.getGroupId();
        long newEpoch = group.getEpoch() + 1;

        // Check if we're the leader (shouldn't initiate election if we are)
        if (group.getLeaderId().equals(localUser.getUserId())) {
            notifyLog("We are the leader - not initiating election");
            return;
        }

        // Check if election already ongoing for this epoch
        ElectionState existingState = ongoingElections.get(groupId);
        if (existingState != null && existingState.epoch == newEpoch) {
            notifyLog("Election already ongoing for epoch " + newEpoch);
            return; // Already in progress
        }

        // Determine the candidate using lexicographic ordering
        // Use the highest user ID among all members (deterministic)
        String determinedCandidate = determineCandidateLexicographically(group);

        notifyLog("Leader suspected for group " + group.getName() +
                " (epoch " + newEpoch + "), deterministic candidate: " + determinedCandidate.substring(0, 8));

        // Only the determined candidate should propose
        if (!determinedCandidate.equals(localUser.getUserId())) {
            // We're not the candidate, just wait for proposal from determined candidate
            notifyLog("Not the candidate (we are " + localUser.getUserId().substring(0, 8) +
                    "), waiting for proposal");
            return;
        }

        // We are the determined candidate, initiate election
        ElectionState state = new ElectionState(group, newEpoch, localUser.getUserId());
        ongoingElections.put(groupId, state);

        notifyLog("Starting election for group " + group.getName() +
                " (epoch " + newEpoch + ") as candidate");

        // Vote for ourselves
        state.recordVote(localUser.getUserId(), localUser.getUserId());
        votedEpochs.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(newEpoch);

        // Broadcast proposal
        ElectionMessage proposal = ElectionMessage.create(
                localUser.getUserId(), groupId,
                ElectionMessage.ElectionType.PROPOSAL,
                localUser.getUserId(), newEpoch);

        broadcastToGroup(group, proposal);

        // Schedule election timeout and finalization
        scheduler.schedule(() -> finalizeElection(groupId), ELECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Determine the candidate using lexicographic ordering (highest user ID wins).
     * This ensures all nodes agree on who should propose.
     */
    private String determineCandidateLexicographically(Group group) {
        List<String> allMemberIds = new ArrayList<>();

        // Add all members
        for (User member : group.getMembers()) {
            allMemberIds.add(member.getUserId());
        }

        // Add current leader (in case they're still in group but failed)
        allMemberIds.add(group.getLeaderId());

        // Sort lexicographically and pick the highest (last in sorted order)
        Collections.sort(allMemberIds);
        return allMemberIds.get(allMemberIds.size() - 1);
    }

    /**
     * Handle incoming election proposal with strict epoch validation.
     */
    public void handleElectionProposal(ElectionMessage message) {
        Group group = groupManager.getGroup(message.getGroupId());
        if (group == null) {
            notifyLog("Ignoring proposal - group not found: " + message.getGroupId());
            return;
        }

        String groupId = message.getGroupId();
        long proposalEpoch = message.getEpoch();

        notifyLog("Received proposal for group " + group.getName() +
                " (epoch " + proposalEpoch + ") from " + message.getSenderId() +
                ", current epoch: " + group.getEpoch());

        // Reject stale proposals
        if (proposalEpoch <= group.getEpoch()) {
            notifyLog("Rejecting stale proposal (epoch " + proposalEpoch +
                    " <= current " + group.getEpoch() + ")");
            return;
        }

        // Check if we've already voted in this epoch
        Set<Long> voted = votedEpochs.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet());
        if (voted.contains(proposalEpoch)) {
            notifyLog("Already voted in epoch " + proposalEpoch);
            return; // Already voted in this epoch
        }

        // Verify this is from the deterministic candidate
        String expectedCandidate = determineCandidateLexicographically(group);
        if (!message.getCandidateId().equals(expectedCandidate)) {
            notifyLog("Rejecting proposal from non-deterministic candidate " +
                    message.getCandidateId() + ", expected " + expectedCandidate);
            return;
        }

        // Record that we voted in this epoch
        voted.add(proposalEpoch);

        // Create or update election state
        ElectionState state = ongoingElections.get(groupId);
        if (state == null || state.epoch < proposalEpoch) {
            state = new ElectionState(group, proposalEpoch, message.getCandidateId());
            ongoingElections.put(groupId, state);
        }

        // Vote for the candidate
        state.recordVote(localUser.getUserId(), message.getCandidateId());

        notifyLog("Voting for " + message.getCandidateId() +
                " in epoch " + proposalEpoch);

        // Send vote back to proposer
        ElectionMessage vote = ElectionMessage.create(
                localUser.getUserId(), groupId,
                ElectionMessage.ElectionType.VOTE,
                message.getCandidateId(), proposalEpoch);

        // Send directly to the candidate
        sendMessageToUser(message.getSenderId(), vote, group);
    }

    /**
     * Handle incoming election vote with idempotency.
     */
    public void handleElectionVote(ElectionMessage message) {
        ElectionState state = ongoingElections.get(message.getGroupId());
        if (state == null || state.epoch != message.getEpoch()) {
            notifyLog("Ignoring vote - no matching election state");
            return; // Not our election or wrong epoch
        }

        // Only the candidate should collect votes
        if (!state.candidateId.equals(localUser.getUserId())) {
            notifyLog("Ignoring vote - not the candidate");
            return;
        }

        notifyLog("Received vote from " + message.getSenderId() +
                " for " + message.getCandidateId() + " (epoch " + message.getEpoch() + ")");

        // Record vote (idempotent - duplicate votes from same voter don't count twice)
        state.recordVote(message.getSenderId(), message.getCandidateId());

        notifyLog("Vote count: " + state.getVoteCount());

        // Check if we have quorum
        Group group = groupManager.getGroup(message.getGroupId());
        if (group != null && state.hasQuorum()) {
            notifyLog("Quorum achieved! Finalizing election.");
            // We have majority - finalize immediately
            finalizeElection(message.getGroupId());
        }
    }

    /**
     * Handle election result announcement with epoch validation.
     */
    public void handleElectionResult(ElectionMessage message) {
        Group group = groupManager.getGroup(message.getGroupId());
        if (group == null) {
            return;
        }

        // Reject stale results
        if (message.getEpoch() <= group.getEpoch()) {
            return;
        }

        Group updatedGroup = group.withNewLeader(message.getCandidateId(), message.getEpoch());
        groupManager.updateGroup(updatedGroup);

        ongoingElections.remove(message.getGroupId());

        // Record leader activity for gossip
        recordLeaderActivity(message.getGroupId());

        notifyLog("New leader elected for group " + group.getName() +
                " - " + message.getCandidateId() + " (epoch " + message.getEpoch() + ")");

        notifyLeaderElected(message.getGroupId(), message.getCandidateId(), message.getEpoch());
    }

    /**
     * Finalize election with quorum requirement and announce result.
     */
    private void finalizeElection(String groupId) {
        ElectionState state = ongoingElections.get(groupId);
        if (state == null) {
            return; // Already finalized
        }

        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            return;
        }

        // Only the candidate should finalize
        if (!state.candidateId.equals(localUser.getUserId())) {
            return;
        }

        // Check if we have quorum
        if (!state.hasQuorum()) {
            notifyLog("Failed to achieve quorum for group " + group.getName());
            ongoingElections.remove(groupId);
            return;
        }

        // Determine winner (should be the candidate since we have quorum)
        String winner = state.getWinner();

        // Update group with new leader
        Group updatedGroup = group.withNewLeader(winner, state.epoch);
        groupManager.updateGroup(updatedGroup);

        // Record leader activity for gossip
        recordLeaderActivity(groupId);

        // Broadcast result
        ElectionMessage result = ElectionMessage.create(
                localUser.getUserId(), groupId,
                ElectionMessage.ElectionType.RESULT,
                winner, state.epoch);

        broadcastToGroup(updatedGroup, result);

        ongoingElections.remove(groupId);

        notifyLog("Election complete for group " + group.getName() +
                " - new leader: " + winner + " (epoch " + state.epoch + ") with " +
                state.getVoteCount() + " votes");

        notifyLeaderElected(groupId, winner, state.epoch);
    }

    /**
     * Send message to a specific user.
     */
    private void sendMessageToUser(String userId, ElectionMessage message, Group group) {
        User target = group.getMembers().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        if (target == null) {
            notifyError("Cannot find user " + userId.substring(0, 8) +
                    " in group members to send vote", null);
            return;
        }

        notifyLog("Sending " + message.getElectionType() + " to " +
                target.getUsername() + " at " + target.getIpAddress() + ":" + target.getRmiPort());

        executor.submit(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(target.getIpAddress(), target.getRmiPort());
                PeerService peerService = (PeerService) registry.lookup("PeerService");
                peerService.receiveMessage(message);
                notifyLog("Successfully sent " + message.getElectionType() +
                        " to " + target.getUsername());
            } catch (Exception e) {
                notifyError("Failed to send " + message.getElectionType() +
                        " to " + target.getUsername(), e);
            }
        });
    }

    /**
     * Broadcast message to all group members (including leader).
     */
    private void broadcastToGroup(Group group, ElectionMessage message) {
        Set<User> allMembers = new HashSet<>(group.getMembers());

        notifyLog("Broadcasting to " + allMembers.size() + " members");

        for (User member : allMembers) {
            if (member.getUserId().equals(localUser.getUserId())) {
                continue;
            }

            notifyLog("Sending to " + member.getUsername() +
                    " at " + member.getIpAddress() + ":" + member.getRmiPort());

            executor.submit(() -> {
                try {
                    Registry registry = LocateRegistry.getRegistry(member.getIpAddress(), member.getRmiPort());
                    PeerService peerService = (PeerService) registry.lookup("PeerService");
                    peerService.receiveMessage(message);
                    notifyLog("Successfully sent to " + member.getUsername());
                } catch (Exception e) {
                    notifyError("Failed to send to " + member.getUsername(), e);
                }
            });
        }
    }

    /**
     * State tracking for an ongoing election with quorum support.
     */
    private static class ElectionState {
        final Group group;
        final long epoch;
        final String candidateId;
        // Track votes: voterId -> candidateId (idempotent - one vote per voter)
        final Map<String, String> votes = new ConcurrentHashMap<>();

        ElectionState(Group group, long epoch, String candidateId) {
            this.group = group;
            this.epoch = epoch;
            this.candidateId = candidateId;
        }

        void recordVote(String voterId, String candidateId) {
            // Idempotent - voters can only vote once
            votes.putIfAbsent(voterId, candidateId);
        }

        String getWinner() {
            // Count votes for each candidate
            Map<String, Long> voteCounts = votes.values().stream()
                    .collect(Collectors.groupingBy(cid -> cid, Collectors.counting()));

            return voteCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(candidateId);
        }

        int getVoteCount() {
            return votes.size();
        }

        boolean hasQuorum() {
            // Election happens when leader fails, so we should NOT count the leader
            // Only count members (excluding the failed leader)
            int activeMembers = group.getMembers().size(); // Members only, no leader
            int quorum = (activeMembers / 2) + 1;

            // Count votes for our candidate
            long votesForCandidate = votes.values().stream()
                    .filter(cid -> cid.equals(candidateId))
                    .count();

            return votesForCandidate >= quorum;
        }
    }

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

    private void notifyLeaderElected(String groupId, String leaderId, long epoch) {
        for (PeerEventListener listener : listeners) {
            listener.onLeaderElected(groupId, leaderId, epoch);
        }
    }
}
