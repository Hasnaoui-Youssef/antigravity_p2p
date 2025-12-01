package p2p.peer.consensus;

import p2p.common.model.*;
import p2p.common.model.message.*;
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

    // Map of requestId -> Future to track pending sync requests
    private final Map<String, CompletableFuture<List<ChatMessage>>> pendingRequests = new ConcurrentHashMap<>();

    // Callback for sync completion notifications
    private SyncCallback syncCallback;

    /**
     * Callback interface for sync completion notifications.
     */
    public interface SyncCallback {
        void onSyncComplete(String groupId, int messageCount);

        void onSyncFailed(String groupId, String reason);
    }

    public void setSyncCallback(SyncCallback callback) {
        this.syncCallback = callback;
    }

    private final VectorClock vectorClock;

    public ConsensusManager(User localUser, GroupManager groupManager, VectorClock vectorClock) {
        this.localUser = localUser;
        this.groupManager = groupManager;
        this.vectorClock = vectorClock;
    }

    /**
     * Initiates a sync for a group by querying a quorum of members.
     */
    public void initiateSync(String groupId, VectorClock lastKnownState) {
        executor.submit(() -> {
            try {
                Group group = groupManager.getGroup(groupId);
                if (group == null) {
                    if (syncCallback != null) {
                        syncCallback.onSyncFailed(groupId, "Group not found");
                    }
                    return;
                }

                List<User> quorum = selectQuorum(group);

                if (quorum.isEmpty()) {
                    if (syncCallback != null) {
                        syncCallback.onSyncFailed(groupId, "No quorum available");
                    }
                    return;
                }

                // Send sync requests to quorum members in parallel
                List<List<ChatMessage>> responses = queryQuorum(groupId, lastKnownState, quorum);

                // Merge responses and apply to local state
                List<ChatMessage> mergedMessages = mergeResponses(responses);
                if (!mergedMessages.isEmpty()) {
                    applyMessages(groupId, mergedMessages);
                    System.out.println("[Consensus] Sync complete for group " + groupId +
                            " - received " + mergedMessages.size() + " messages");

                    if (syncCallback != null) {
                        syncCallback.onSyncComplete(groupId, mergedMessages.size());
                    }
                } else {
                    if (syncCallback != null) {
                        syncCallback.onSyncComplete(groupId, 0);
                    }
                }

            } catch (Exception e) {
                System.err.println("[Consensus] Sync failed for group " + groupId + ": " + e.getMessage());
                if (syncCallback != null) {
                    syncCallback.onSyncFailed(groupId, e.getMessage());
                }
            }
        });
    }

    /**
     * Select quorum members (N/2 + 1).
     * For small groups (3 members), we need special handling:
     * - If we exclude the leader and ourselves, only 1 member remains
     * - In this case, we query that single member
     */
    private List<User> selectQuorum(Group group) {
        List<User> members = group.getMembers().stream()
                .filter(u -> !u.getUserId().equals(localUser.getUserId()))
                .collect(Collectors.toList());

        if (members.isEmpty())
            return Collections.emptyList();

        // Total = members (excluding leader) + 1 for leader
        // Note: group.getMembers() excludes leader by design
        int totalGroupSize = group.getMembers().size() + 1;
        int quorumSize;

        if (totalGroupSize <= 3) {
            // Small group: query all available members (at least 1)
            quorumSize = members.size();
        } else {
            quorumSize = (members.size() / 2) + 1;
        }

        // Return up to quorumSize members, but never more than available
        int limit = Math.min(members.size(), Math.max(1, quorumSize));
        return members.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Query quorum members in parallel.
     */
    private List<List<ChatMessage>> queryQuorum(String groupId, VectorClock lastKnownState, List<User> quorum) {
        List<CompletableFuture<List<ChatMessage>>> futures = quorum.stream()
                .map(member -> querySingleMember(groupId, lastKnownState, member))
                .collect(Collectors.toList());

        // Wait for all responses (with timeout)
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
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
                        return Collections.<ChatMessage>emptyList();
                    }
                })
                .filter(list -> !list.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Query a single member for missing messages.
     */
    private CompletableFuture<List<ChatMessage>> querySingleMember(String groupId, VectorClock lastKnownState,
            User member) {
        CompletableFuture<List<ChatMessage>> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();

        // Store future to be completed when response arrives
        pendingRequests.put(requestId, future);

        // Schedule timeout cleanup
        executor.submit(() -> {
            try {
                Thread.sleep(5000);
                if (pendingRequests.remove(requestId) != null) {
                    future.complete(Collections.emptyList());
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        });

        executor.submit(() -> {
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    SyncRequest request = new SyncRequest(
                            requestId,
                            localUser.getUserId(),
                            System.currentTimeMillis(),
                            groupId,
                            lastKnownState);

                    Registry registry = LocateRegistry.getRegistry(member.getIpAddress(), member.getRmiPort());
                    PeerService peerService = (PeerService) registry.lookup("PeerService");

                    peerService.receiveMessage(request);
                    return; // Success

                } catch (Exception e) {
                    if (i == maxRetries - 1) {
                        System.err
                                .println("[Consensus] Failed to query " + member.getUsername() + " after " + maxRetries
                                        + " attempts: " + e.getMessage());
                        pendingRequests.remove(requestId);
                        future.complete(Collections.emptyList());
                    } else {
                        try {
                            Thread.sleep(500); // Backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        });

        return future;
    }

    /**
     * Merge messages from multiple responses, resolving conflicts.
     */
    private List<ChatMessage> mergeResponses(List<List<ChatMessage>> responses) {
        // Use a map to deduplicate by message ID
        Set<ChatMessage> messageMap = new HashSet<>();

        for (List<ChatMessage> response : responses) {
            for (ChatMessage msg : response) {
                // Keep the message if we don't have it, or if it's newer
                messageMap.add(msg);
            }
        }

        // Sort by timestamp for ordered application
        return messageMap.stream()
                .sorted(new CausalOrderComparator())
                .collect(Collectors.toList());
    }

    /**
     * Apply merged messages to local state.
     */
    private void applyMessages(String groupId, List<ChatMessage> messages) {
        groupManager.addMessages(groupId, messages);
    }

    /**
     * Handle incoming sync request from another peer.
     */
    public void handleSyncRequest(String groupId, VectorClock senderClock, User requester, String requestId) {
        try {
            Group group = groupManager.getGroup(groupId);
            if (group == null) {
                return;
            }

            // Get all messages for the group
            List<ChatMessage> allMessages = groupManager.getMessages(groupId);

            // Filter to messages the requester doesn't have
            List<ChatMessage> missingMessages = allMessages.stream()
                    .filter(msg -> shouldSendMessage(msg, senderClock))
                    .collect(Collectors.toList());

            if (missingMessages.isEmpty()) {
                // Send empty response to complete the future on the other side
                sendSyncResponse(requester, requestId, groupId, Collections.emptyList());
                return;
            }

            sendSyncResponse(requester, requestId, groupId, missingMessages);

        } catch (Exception e) {
            System.err.println("[Consensus] Failed to handle sync request: " + e.getMessage());
        }
    }

    private void sendSyncResponse(User requester, String requestId, String groupId, List<ChatMessage> messages) {
        try {
            SyncResponse response = SyncResponse.create(localUser.getUserId(), requestId, groupId, messages,
                    vectorClock.clone());

            Registry registry = LocateRegistry.getRegistry(requester.getIpAddress(), requester.getRmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");
            peerService.receiveMessage(response);
        } catch (Exception e) {
            System.err.println("[Consensus] Failed to send sync response: " + e.getMessage());
        }
    }

    /**
     * Determine if a message should be sent based on sender's vector clock.
     */
    private boolean shouldSendMessage(Message msg, VectorClock senderClock) {
        VectorClock msgClock = msg.getVectorClock();

        // If the message has a vector clock, check if the sender has seen it
        if (msgClock != null) {
            // If senderClock >= msgClock, they have seen it.
            // We send if NOT (senderClock >= msgClock)
            // i.e., if msgClock is NOT causally preceding or equal to senderClock
            return !msgClock.happensBefore(senderClock) && !msgClock.equals(senderClock);
        }

        // Fallback for messages without vector clocks (shouldn't happen in groups
        // usually)
        return true;
    }

    /**
     * Handle incoming sync response.
     */
    public void handleSyncResponse(SyncResponse response) {
        String requestId = response.getRequestId();
        CompletableFuture<List<ChatMessage>> future = pendingRequests.remove(requestId);

        if (future != null) {
            future.complete(response.getMissingMessages());
        }
    }
}
