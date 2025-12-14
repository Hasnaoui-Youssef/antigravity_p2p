package p2p.peer.util;

import p2p.common.model.GroupEvent;
import p2p.common.model.MessageTopic;
import p2p.common.model.User;
import p2p.common.model.message.ChatMessage;
import p2p.common.model.message.GroupInvitationMessage;
import p2p.common.model.message.Message;
import p2p.common.model.message.ChatMessage.ChatSubtopic;
import p2p.peer.PeerController;
import p2p.peer.PeerEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Utility for waiting for a specific group event across multiple peers.
 * Ensures that each peer is counted only once, even if they receive multiple
 * notifications.
 * 
 * IMPORTANT: Create the waiter BEFORE triggering the action that causes the event.
 * This ensures the listener is attached before any events fire.
 * 
 * For GROUP_CREATED events where the groupId is not known in advance, pass null
 * for groupId to match any group.
 */
public class MultiPeerGroupEventWaiter {

    private final CountDownLatch latch;
    private final Set<String> succeededPeerIds = ConcurrentHashMap.newKeySet();
    private final Map<String, PeerController> peersMap = new ConcurrentHashMap<>();
    private final Map<String, PeerEventListener> listenersMap = new ConcurrentHashMap<>();
    private final AtomicReference<String> matchedGroupId = new AtomicReference<>(null);

    /**
     * Creates a waiter for a specific group event.
     * 
     * @param groupId The group ID to match, or null to match any group (useful for GROUP_CREATED)
     * @param expectedEvent The event type to wait for
     * @param peers The list of peers that should all receive the event
     */
    public MultiPeerGroupEventWaiter(String groupId, TestEvent expectedEvent, List<PeerController> peers) {
        this.latch = new CountDownLatch(peers.size());

        for (PeerController peer : peers) {
            String userId = peer.getLocalUser().userId();
            peersMap.put(userId, peer);

            PeerEventListener listener = new PeerEventListener() {
                @Override
                public void onGroupEvent(String gId, GroupEvent eventType, String message) {
                    // If groupId is null, match any group; otherwise match exactly
                    if (groupId != null && !gId.equals(groupId))
                        return;

                    try {
                        TestEvent testEvent = TestEvent.fromGroupEvent(eventType);
                        if (testEvent == expectedEvent) {
                            // Thread-safe: only first thread to set wins
                            matchedGroupId.compareAndSet(null, gId);
                            if (succeededPeerIds.add(userId)) {
                                latch.countDown();
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        // Ignore events that don't map to TestEvent
                    }
                }

                @Override
                public void onLeaderElected(String gId, String leaderId, long epoch) {
                    if (groupId != null && !gId.equals(groupId))
                        return;
                    if (expectedEvent == TestEvent.LEADER_ELECTED) {
                        // Thread-safe: only first thread to set wins
                        matchedGroupId.compareAndSet(null, gId);
                        if (succeededPeerIds.add(userId)) {
                            latch.countDown();
                        }
                    }
                }

                @Override
                public void onFriendRequest(User requester) {
                }

                @Override
                public void onFriendRequestAccepted(User accepter) {
                }

                @Override
                public void onMessageReceived(Message message) {
                    if (expectedEvent != TestEvent.MESSAGE_RECEIVED)
                        return;
                    if (message.getTopic() != MessageTopic.CHAT)
                        return;
                    if (!(message instanceof ChatMessage groupChatMessage))
                        return;
                    if (groupChatMessage.getSubtopic() != ChatSubtopic.GROUP)
                        return;
                    if (groupId != null && !groupChatMessage.getGroupId().equals(groupId))
                        return;
                    // Thread-safe: only first thread to set wins
                    matchedGroupId.compareAndSet(null, groupChatMessage.getGroupId());
                    if (succeededPeerIds.add(userId)) {
                        latch.countDown();
                    }
                }

                @Override
                public void onGroupInvitation(GroupInvitationMessage request) {
                }

                @Override
                public void onError(String message, Throwable t) {
                }

                @Override
                public void onLog(String message) {
                }
            };

            listenersMap.put(userId, listener);
            peer.addEventListener(listener);
        }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    public void cleanup() {
        for (Map.Entry<String, PeerEventListener> entry : listenersMap.entrySet()) {
            PeerController peer = peersMap.get(entry.getKey());
            if (peer != null) {
                peer.removeEventListener(entry.getValue());
            }
        }
    }
    
    /**
     * Returns the groupId that was matched (useful when waiting for any group).
     */
    public String getMatchedGroupId() {
        return matchedGroupId.get();
    }

    public List<String> getSucceededPeers() {
        return succeededPeerIds.stream()
                .map(id -> peersMap.get(id).getLocalUser().username())
                .collect(Collectors.toList());
    }

    public List<String> getPendingPeers() {
        return peersMap.keySet().stream()
                .filter(id -> !succeededPeerIds.contains(id))
                .map(id -> peersMap.get(id).getLocalUser().username())
                .collect(Collectors.toList());
    }
}
