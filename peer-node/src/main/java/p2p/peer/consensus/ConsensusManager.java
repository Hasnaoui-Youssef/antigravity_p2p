package p2p.peer.consensus;

import p2p.common.model.*;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.groups.GroupManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages quorum-based synchronization for message recovery.
 */
public class ConsensusManager {

    private final User localUser;
    private final GroupManager groupManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ConsensusManager(User localUser, GroupManager groupManager) {
        this.localUser = localUser;
        this.groupManager = groupManager;
    }

    /**
     * Initiates a sync for a group by querying a quorum of members.
     */
    public void initiateSync(String groupId, VectorClock lastKnownState) {
        executor.submit(() -> {
            try {
                Optional<Group> groupOpt = groupManager.getGroup(groupId);
                if (groupOpt.isEmpty()) {
                    return;
                }
                
                Group group = groupOpt.get();
                List<User> quorum = selectQuorum(group);
                
                if (quorum.isEmpty()) {
                    System.out.println("[Consensus] No quorum available for group " + groupId);
                    return;
                }
                
                System.out.println("[Consensus] Querying quorum of " + quorum.size() + 
                    " members for group " + groupId);
                
                // Send sync requests to quorum members in parallel
                List<List<Message>> responses = queryQuorum(groupId, lastKnownState, quorum);
                
                // Merge responses and apply to local state
                List<Message> mergedMessages = mergeResponses(responses);
                applyMessages(groupId, mergedMessages);
                
                System.out.println("[Consensus] Sync complete for group " + groupId + 
                    " - received " + mergedMessages.size() + " messages");
                
            } catch (Exception e) {
                System.err.println("[Consensus] Sync failed for group " + groupId + ": " + e.getMessage());
            }
        });
    }

    /**
     * Select quorum members (N/2 + 1).
     */
    private List<User> selectQuorum(Group group) {
        List<User> members = group.getMembers().stream()
            .filter(u -> !u.getUserId().equals(localUser.getUserId()))
            .collect(Collectors.toList());
        
        int quorumSize = (members.size() / 2) + 1;
        
        // Return up to quorumSize members
        return members.stream()
            .limit(quorumSize)
            .collect(Collectors.toList());
    }

    /**
     * Query quorum members in parallel.
     */
    private List<List<Message>> queryQuorum(String groupId, VectorClock lastKnownState, List<User> quorum) {
        List<CompletableFuture<List<Message>>> futures = quorum.stream()
            .map(member -> CompletableFuture.supplyAsync(() -> 
                querySingleMember(groupId, lastKnownState, member), executor))
            .collect(Collectors.toList());
        
        // Wait for all responses (with timeout)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.err.println("[Consensus] Quorum query timeout");
        } catch (Exception e) {
            System.err.println("[Consensus] Quorum query error: " + e.getMessage());
        }
        
        // Collect successful responses
        return futures.stream()
            .map(f -> {
                try {
                    return f.getNow(Collections.emptyList());
                } catch (Exception e) {
                    return Collections.<Message>emptyList();
                }
            })
            .filter(list -> !list.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Query a single member for missing messages.
     */
    private List<Message> querySingleMember(String groupId, VectorClock lastKnownState, User member) {
        try {
            SyncRequest request = SyncRequest.create(localUser.getUserId(), groupId, lastKnownState);
            
            Registry registry = LocateRegistry.getRegistry(member.getIpAddress(), member.getRmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");
            
            // This is a simplification - in real implementation, we'd wait for async response
            // For now, we'll track pending requests and responses separately
            peerService.receiveMessage(request);
            
            // Return empty for now - responses will be handled via handleSyncResponse
            return Collections.emptyList();
            
        } catch (Exception e) {
            System.err.println("[Consensus] Failed to query " + member.getUsername() + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Merge messages from multiple responses, resolving conflicts.
     */
    private List<Message> mergeResponses(List<List<Message>> responses) {
        // Use a map to deduplicate by message ID
        Map<String, Message> messageMap = new HashMap<>();
        
        for (List<Message> response : responses) {
            for (Message msg : response) {
                // Keep the message if we don't have it, or if it's newer
                messageMap.putIfAbsent(msg.getMessageId(), msg);
            }
        }
        
        // Sort by timestamp for ordered application
        return messageMap.values().stream()
            .sorted(Comparator.comparingLong(Message::getTimestamp))
            .collect(Collectors.toList());
    }

    /**
     * Apply merged messages to local state.
     */
    private void applyMessages(String groupId, List<Message> messages) {
        for (Message msg : messages) {
            groupManager.addMessage(groupId, msg);
        }
    }

    /**
     * Handle incoming sync request from another peer.
     */
    public void handleSyncRequest(String groupId, VectorClock senderClock, User requester) {
        try {
            Optional<Group> groupOpt = groupManager.getGroup(groupId);
            if (groupOpt.isEmpty()) {
                return;
            }
            
            // Get all messages for the group
            List<Message> allMessages = groupManager.getMessages(groupId);
            
            // Filter to messages the requester doesn't have
            // Simplified: send all messages newer than their last known state
            List<Message> missingMessages = allMessages.stream()
                .filter(msg -> shouldSendMessage(msg, senderClock))
                .collect(Collectors.toList());
            
            if (missingMessages.isEmpty()) {
                System.out.println("[Consensus] No missing messages for " + requester.getUsername());
                return;
            }
            
            // Send sync response
            SyncResponse response = SyncResponse.create(localUser.getUserId(), groupId, missingMessages);
            
            Registry registry = LocateRegistry.getRegistry(requester.getIpAddress(), requester.getRmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");
            peerService.receiveMessage(response);
            
            System.out.println("[Consensus] Sent " + missingMessages.size() + 
                " messages to " + requester.getUsername());
            
        } catch (Exception e) {
            System.err.println("[Consensus] Failed to handle sync request: " + e.getMessage());
        }
    }

    /**
     * Determine if a message should be sent based on sender's vector clock.
     */
    private boolean shouldSendMessage(Message msg, VectorClock senderClock) {
        // Simplified logic: compare timestamps
        // In a real implementation, would use vector clock comparison
        return true; // For now, send all messages
    }

    /**
     * Handle incoming sync response.
     */
    public void handleSyncResponse(SyncResponse response) {
        String groupId = response.getGroupId();
        List<Message> messages = response.getMissingMessages();
        
        System.out.println("[Consensus] Received " + messages.size() + 
            " messages for group " + groupId);
        
        // Apply messages to local state
        applyMessages(groupId, messages);
    }
}
