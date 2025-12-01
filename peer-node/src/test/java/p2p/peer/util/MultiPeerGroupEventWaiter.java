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
import java.util.stream.Collectors;

/**
 * Utility for waiting for a specific group event across multiple peers.
 * Ensures that each peer is counted only once, even if they receive multiple
 * notifications.
 */
public class MultiPeerGroupEventWaiter {

    private final CountDownLatch latch;
    private final Set<String> succeededPeerIds = ConcurrentHashMap.newKeySet();
    private final Map<String, PeerController> peersMap = new ConcurrentHashMap<>();
    private final Map<String, PeerEventListener> listenersMap = new ConcurrentHashMap<>();

    public MultiPeerGroupEventWaiter(String groupId, TestEvent expectedEvent, List<PeerController> peers) {
        this.latch = new CountDownLatch(peers.size());

        for (PeerController peer : peers) {
            String userId = peer.getLocalUser().getUserId();
            peersMap.put(userId, peer);

            PeerEventListener listener = new PeerEventListener() {
                @Override
                public void onGroupEvent(String gId, GroupEvent eventType, String message) {
                    if (!gId.equals(groupId))
                        return;

                    try {
                        TestEvent testEvent = TestEvent.fromGroupEvent(eventType);
                        if (testEvent == expectedEvent) {
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
                    if (expectedEvent == TestEvent.LEADER_ELECTED && gId.equals(groupId)) {
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
                    if (!groupChatMessage.getGroupId().equals(groupId))
                        return;
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

    public List<String> getSucceededPeers() {
        return succeededPeerIds.stream()
                .map(id -> peersMap.get(id).getLocalUser().getUsername())
                .collect(Collectors.toList());
    }

    public List<String> getPendingPeers() {
        return peersMap.keySet().stream()
                .filter(id -> !succeededPeerIds.contains(id))
                .map(id -> peersMap.get(id).getLocalUser().getUsername())
                .collect(Collectors.toList());
    }
}
