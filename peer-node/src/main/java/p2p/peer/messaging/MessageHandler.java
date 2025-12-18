package p2p.peer.messaging;

import p2p.common.model.message.ChatMessage;
import p2p.common.model.User;
import p2p.common.model.Group;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.PeerEventListener;
import p2p.peer.friends.FriendManager;
import p2p.peer.groups.GroupManager;
import p2p.peer.groups.LeaderElectionManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles sending and receiving messages between peers.
 */
public class MessageHandler {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final User localUser;
    private final VectorClock vectorClock;
    private final FriendManager friendManager;
    private final GroupManager groupManager;
    private final LeaderElectionManager electionManager;
    private final List<PeerEventListener> listeners = new CopyOnWriteArrayList<>();

    public MessageHandler(User localUser, VectorClock vectorClock, FriendManager friendManager,
            GroupManager groupManager, LeaderElectionManager electionManager) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
        this.friendManager = friendManager;
        this.groupManager = groupManager;
        this.electionManager = electionManager;
    }

    public void addEventListener(PeerEventListener listener) {
        listeners.add(listener);
    }

    public void removeEventListener(PeerEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Send a message to a friend.
     */
    public void sendMessage(User recipient, String content) throws Exception {
        if (!friendManager.isFriend(recipient.userId())) {
            notifyLog("Cannot send to " + recipient.username() + " - not friends");
            return;
        }

        // Increment vector clock
        synchronized (vectorClock) {
            vectorClock.increment(localUser.userId());
        }

        // Create message using the new ChatMessage type
        ChatMessage message = ChatMessage.createDirect(localUser, recipient.userId(), content, vectorClock.clone());

        friendManager.addMessage(recipient.userId(), message);

        // Send via RMI
        Registry registry = LocateRegistry.getRegistry(recipient.ipAddress(), recipient.rmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.receiveMessage(message);

        // Print confirmation
        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        notifyLog("[" + time + "] You -> " + recipient.username() + ": " + content);
    }

    /**
     * Handle incoming ChatMessage.
     */
    public void handleIncomingChatMessage(ChatMessage message) {
        // Validate message type
        if (message.getSubtopic() == ChatMessage.ChatSubtopic.DIRECT) {
            handleDirectChatMessage(message);
        } else if (message.getSubtopic() == ChatMessage.ChatSubtopic.GROUP) {
            handleGroupChatMessage(message);
        }
    }

    private void handleDirectChatMessage(ChatMessage message) {
        User sender = friendManager.getFriendById(message.getSenderId());
        if (sender == null) {
            return;
        }

        friendManager.addMessage(message.getSenderId(), message);

        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        notifyLog("\n[" + time + "] " + message.getSenderUsername() + ": " + message.getContent());

        VectorClock msgClock = message.getVectorClock();
        if (msgClock != null) {
            notifyLog("Vector Clock: " + msgClock);
        }
    }

    private void handleGroupChatMessage(ChatMessage message) {
        groupManager.addMessage(message.getGroupId(), message);

        Group group = groupManager.getGroup(message.getGroupId());
        if (group != null) {
            // Log leader activity if message is FROM leader
            if (message.getSenderId().equals(group.leader().userId())) {
                electionManager.recordLeaderActivity(message.getGroupId());
            }

            // If WE are the leader, and message is NOT from us, propagate it
            if (localUser.userId().equals(group.leader().userId()) &&
                    !message.getSenderId().equals(localUser.userId())) {
                broadcastMessageToGroup(group, message);
            }
        }

        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        notifyLog("\n[Group " + message.getGroupId() + "] [" + time + "] " + message.getSenderUsername() + ": "
                + message.getContent());
        VectorClock msgClock = message.getVectorClock();
        if (msgClock != null) {
            notifyLog("Vector Clock: " + msgClock);
        }
    }

    private void broadcastMessageToGroup(Group group, ChatMessage message) {
        for (User member : group.activeMembers()) {
            if (member.userId().equals(localUser.userId()) || message.getSenderId().equals(member.userId())) {
                continue; // Skip self
            }
            // Note: We broadcast to everyone else, including the original sender
            // (confirmation)
            try {
                Registry registry = LocateRegistry.getRegistry(member.ipAddress(), member.rmiPort());
                PeerService peerService = (PeerService) registry.lookup("PeerService");
                peerService.receiveMessage(message);
            } catch (Exception e) {
                notifyLog("Failed to propagate message to " + member.username());
            }
        }
    }

    private void notifyLog(String message) {
        for (PeerEventListener listener : listeners) {
            listener.onLog(message);
        }
    }
}
