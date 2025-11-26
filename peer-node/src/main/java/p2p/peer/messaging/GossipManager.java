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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implements Cassandra-style gossip protocol with digest comparison.
 */
public class GossipManager {

    private final User localUser;
    private final GroupManager groupManager;
    private ConsensusManager consensusManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    public GossipManager(User localUser, GroupManager groupManager) {
        this.localUser = localUser;
        this.groupManager = groupManager;
    }
    
    public void setConsensusManager(ConsensusManager consensusManager) {
        this.consensusManager = consensusManager;
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
                
                // Create gossip message with our latest vector clock for this group
                // For simplicity, we use the group's latest message timestamp or similar
                // But better: send our VectorClock for this group
                
                // In a real system, we'd track per-group vector clocks.
                // Here we use the global vector clock as a proxy or the last message's clock
                
                GossipMessage gossip = GossipMessage.create(
                    localUser, 
                    group.getGroupId(), 
                    groupManager.getLatestClock(group.getGroupId())
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
     * Handle incoming gossip message by comparing vector clocks.
     */
    public void handleGossipMessage(GossipMessage message) {
        String groupId = message.getGroupId();
        VectorClock remoteClock = message.getVectorClock();
        
        // Check if we have this group
        if (groupManager.getGroup(groupId) == null) {
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
    }
}
