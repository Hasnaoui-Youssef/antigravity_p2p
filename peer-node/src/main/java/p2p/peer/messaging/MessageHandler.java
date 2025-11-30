package p2p.peer.messaging;

import p2p.common.model.message.ChatMessage;
import p2p.common.model.message.ChatSubtopic;
import p2p.common.model.User;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.PeerEventListener;
import p2p.peer.friends.FriendManager;

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
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    
    private final User localUser;
    private final VectorClock vectorClock;
    private final FriendManager friendManager;
    private final List<PeerEventListener> listeners = new CopyOnWriteArrayList<>();
    
    public MessageHandler(User localUser, VectorClock vectorClock, FriendManager friendManager) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
        this.friendManager = friendManager;
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
        if (!friendManager.isFriend(recipient.getUserId())) {
            notifyLog("Cannot send to " + recipient.getUsername() + " - not friends");
            return;
        }
        
        // Increment vector clock
        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }
        
        // Create message using the new ChatMessage type
        ChatMessage message = ChatMessage.createDirect(localUser, recipient.getUserId(), content, vectorClock.clone());
        
        // Send via RMI
        Registry registry = LocateRegistry.getRegistry(recipient.getIpAddress(), recipient.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.receiveMessage(message);
        
        // Print confirmation
        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        notifyLog("[" + time + "] You → " + recipient.getUsername() + ": " + content);
    }
    
    /**
     * Handle incoming ChatMessage.
     */
    public void handleIncomingChatMessage(ChatMessage message) {
        // Validate message type
        if (message.getSubtopic() == ChatSubtopic.DIRECT) {
            handleDirectChatMessage(message);
        } else if (message.getSubtopic() == ChatSubtopic.GROUP) {
            handleGroupChatMessage(message);
        }
    }

    private void handleDirectChatMessage(ChatMessage message) {
        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        notifyLog("\n[" + time + "] " + message.getSenderUsername() + ": " + message.getContent());
        VectorClock msgClock = message.getVectorClock();
        if (msgClock != null) {
            notifyLog("Vector Clock: " + msgClock);
        }
    }

    private void handleGroupChatMessage(ChatMessage message) {
        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        notifyLog("\n[Group " + message.getGroupId() + "] [" + time + "] " + message.getSenderUsername() + ": " + message.getContent());
        VectorClock msgClock = message.getVectorClock();
        if (msgClock != null) {
            notifyLog("Vector Clock: " + msgClock);
        }
    }

    private void notifyLog(String message) {
        for (PeerEventListener listener : listeners) {
            listener.onLog(message);
        }
    }
}
