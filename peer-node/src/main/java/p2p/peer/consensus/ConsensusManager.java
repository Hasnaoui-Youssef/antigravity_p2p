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
    private final ScheduledExecutorService executor = Executors
            .newScheduledThreadPool(2);

    // Map of requestId -> Future to track pending sync requests
    private final Map<String, CompletableFuture<List<ChatMessage>>> pendingRequests = new ConcurrentHashMap<>();

    // Track active incoming sync requests being processed
    private final java.util.concurrent.atomic.AtomicInteger activeSyncRequests = new java.util.concurrent.atomic.AtomicInteger(
            0);

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
        List<User> members = group.activeMembers().stream()
                .filter(u -> !u.userId().equals(localUser.userId()))
                .toList();

        if (members.isEmpty())
            return Collections.emptyList();

        // Total = members (excluding leader) + 1 for leader
        // Note: group.getMembers() excludes leader by design
        int totalGroupSize = group.members().size() + 1;
        int quorumSize;

        if (totalGroupSize <= 3) {
            // Small group: query all available members (at least 1)
            quorumSize = members.size();
        } else {
            quorumSize = (members.size() / 2) + 1;
        }

        // Return up to quorumSize members, but never more than available
        int limit = Math.min(members.size(), quorumSize);
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
                .toList();

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

        pendingRequests.put(requestId, future);

        ScheduledFuture<?> timeoutTask = executor.schedule(() -> {
            if (pendingRequests.remove(requestId) != null) {
                future.complete(Collections.emptyList());
            }
        }, 5, TimeUnit.SECONDS);

        userQueryRetry(requestId, groupId, lastKnownState, member, 0, 3, timeoutTask, future);
        return future;
    }

    private void userQueryRetry(String requestId, String groupId, VectorClock lastKnownState, User member,
            int currAttempt, int maxAttempt,
            ScheduledFuture<?> timeoutTask, CompletableFuture<List<ChatMessage>> future) {
        if (currAttempt >= maxAttempt || future.isDone())
            return;
        try {
            SyncRequest request = new SyncRequest(
                    requestId,
                    localUser.userId(),
                    System.currentTimeMillis(),
                    groupId,
                    lastKnownState);

            Registry registry = LocateRegistry.getRegistry(member.ipAddress(), member.rmiPort());
            PeerService peerService = (PeerService) registry.lookup("PeerService");

            peerService.receiveMessage(request);

        } catch (Exception e) {
            if (currAttempt == maxAttempt - 1) {
                if (pendingRequests.remove(requestId) != null) {
                    timeoutTask.cancel(false);
                    future.complete(Collections.emptyList());
                }
            } else {
                long backoff = 500L * (1L << currAttempt);
                executor.schedule(() -> userQueryRetry(requestId, groupId, lastKnownState, member, currAttempt + 1,
                        maxAttempt, timeoutTask, future), backoff, TimeUnit.MILLISECONDS);
            }
        }

    }

    /**
     * Merge messages from multiple responses, resolving conflicts.
     */
    private List<ChatMessage> mergeResponses(List<List<ChatMessage>> responses) {
        // Use a map to deduplicate by message ID
        Set<ChatMessage> messageMap = new HashSet<>();

        for (List<ChatMessage> response : responses) {
            messageMap.addAll(response);
        }

        // Sort by vector clock for ordered application
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
        activeSyncRequests.incrementAndGet();
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
        } finally {
            activeSyncRequests.decrementAndGet();
        }
    }

    /**
     * Wait for active incoming sync requests to complete.
     */
    public void waitForActiveRequests() {
        long deadline = System.currentTimeMillis() + 3000; // 3 seconds timeout
        while (activeSyncRequests.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendSyncResponse(User requester, String requestId, String groupId, List<ChatMessage> messages) {
        try {
            SyncResponse response = SyncResponse.create(localUser.userId(), requestId, groupId, messages,
                    vectorClock.clone());

            Registry registry = LocateRegistry.getRegistry(requester.ipAddress(), requester.rmiPort());
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
