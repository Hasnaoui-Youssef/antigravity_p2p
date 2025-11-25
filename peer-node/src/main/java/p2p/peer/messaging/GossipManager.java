package p2p.peer.messaging;

import p2p.common.model.GossipMessage;
import p2p.common.model.Group;
import p2p.common.model.Message;
import p2p.common.model.SyncRequest;
import p2p.common.model.User;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.groups.GroupManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implements Cassandra-style gossip protocol with digest comparison.
 */
public class GossipManager {

    private final User localUser;
    private final GroupManager groupManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    public GossipManager(User localUser, GroupManager groupManager) {
        this.localUser = localUser;
        this.groupManager = groupManager;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::gossip, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    /**
     * Periodic gossip round.
     */
    private void gossip() {
        List<Group> groups = groupManager.getGroups();
        if (groups.isEmpty()) {
            return;
        }

        // Select a random group
        Group group = groups.get(random.nextInt(groups.size()));
        
        // Select a random member (excluding self)
        List<User> members = group.getMembers();
        if (members.size() <= 1) {
            return;
        }
        
        User target = null;
        int attempts = 0;
        while (target == null && attempts < 5) {
            User candidate = members.get(random.nextInt(members.size()));
            if (!candidate.getUserId().equals(localUser.getUserId())) {
                target = candidate;
            }
            attempts++;
        }
        
        if (target != null) {
            sendGossipDigest(target);
        }
    }

    /**
     * Sends gossip digest to target peer.
     */
    private void sendGossipDigest(User target) {
        try {
            Map<String, Long> digests = computeDigests();
            GossipMessage gossip = GossipMessage.create(localUser.getUserId(), digests);

            Registry registry = LocateRegistry.getRegistry(target.getIpAddress(), target.getRmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");
            peerService.receiveMessage(gossip);
            
        } catch (Exception e) {
            System.err.println("[Gossip] Failed to gossip with " + target.getUsername() + ": " + e.getMessage());
        }
    }

    /**
     * Compute digests (latest message timestamp) for all groups.
     */
    private Map<String, Long> computeDigests() {
        Map<String, Long> digests = new HashMap<>();
        
        for (Group group : groupManager.getGroups()) {
            List<Message> messages = groupManager.getMessages(group.getGroupId());
            long maxTimestamp = messages.stream()
                .mapToLong(Message::getTimestamp)
                .max()
                .orElse(0L);
            
            digests.put(group.getGroupId(), maxTimestamp);
        }
        
        return digests;
    }

    /**
     * Handle incoming gossip message by comparing digests.
     */
    public void handleGossipMessage(GossipMessage message, User sender) {
        Map<String, Long> remoteDigests = message.getGroupDigests();
        Map<String, Long> localDigests = computeDigests();

        // Compare digests and identify gaps
        for (Map.Entry<String, Long> entry : remoteDigests.entrySet()) {
            String groupId = entry.getKey();
            long remoteMaxTimestamp = entry.getValue();
            
            // Check if we have this group
            if (!groupManager.getGroup(groupId).isPresent()) {
                continue; // We're not in this group
            }
            
            long localMaxTimestamp = localDigests.getOrDefault(groupId, 0L);
            
            // If remote has newer messages, request sync
            if (remoteMaxTimestamp > localMaxTimestamp) {
                System.out.println("[Gossip] Detected gap in group " + groupId + 
                    " (local: " + localMaxTimestamp + ", remote: " + remoteMaxTimestamp + ")");
                requestSync(groupId, sender);
            }
        }
    }

    /**
     * Request synchronization for a group.
     */
    private void requestSync(String groupId, User target) {
        try {
            // Get our last known state (simplified: use latest message timestamp)
            List<Message> messages = groupManager.getMessages(groupId);
            VectorClock lastKnownState = new VectorClock();
            
            // Extract vector clock from last group message
            if (!messages.isEmpty()) {
                Message lastMsg = messages.get(messages.size() - 1);
                // Only GroupMessage and DirectMessage have vector clocks
                if (lastMsg instanceof p2p.common.model.GroupMessage) {
                    p2p.common.model.GroupMessage groupMsg = (p2p.common.model.GroupMessage) lastMsg;
                    if (groupMsg.getVectorClock() != null) {
                        lastKnownState = groupMsg.getVectorClock();
                    }
                } else if (lastMsg instanceof p2p.common.model.DirectMessage) {
                    p2p.common.model.DirectMessage directMsg = (p2p.common.model.DirectMessage) lastMsg;
                    if (directMsg.getVectorClock() != null) {
                        lastKnownState = directMsg.getVectorClock();
                    }
                }
            }
            
            SyncRequest syncRequest = SyncRequest.create(localUser.getUserId(), groupId, lastKnownState);
            
            Registry registry = LocateRegistry.getRegistry(target.getIpAddress(), target.getRmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");
            peerService.receiveMessage(syncRequest);
            
            System.out.println("[Gossip] Sent sync request to " + target.getUsername() + " for group " + groupId);
            
        } catch (Exception e) {
            System.err.println("[Gossip] Failed to request sync: " + e.getMessage());
        }
    }
}
