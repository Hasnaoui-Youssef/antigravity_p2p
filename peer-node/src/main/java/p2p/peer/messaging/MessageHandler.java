package p2p.peer.messaging;

import p2p.common.model.DirectMessage;
import p2p.common.model.User;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.friends.FriendManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Handles sending and receiving messages between peers.
 */
public class MessageHandler {
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    
    private final User localUser;
    private final VectorClock vectorClock;
    private final FriendManager friendManager;
    
    public MessageHandler(User localUser, VectorClock vectorClock, FriendManager friendManager) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
        this.friendManager = friendManager;
    }
    
    /**
     * Send a message to a friend.
     */
    public void sendMessage(User recipient, String content) throws Exception {
        if (!friendManager.isFriend(recipient.getUserId())) {
            System.out.println("[Message] Cannot send to " + recipient.getUsername() + " - not friends");
            return;
        }
        
        // Increment vector clock
        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }
        
        // Create message
        DirectMessage message = DirectMessage.create(localUser, recipient.getUserId(), content, vectorClock.clone());
        
        // Send via RMI
        Registry registry = LocateRegistry.getRegistry(recipient.getIpAddress(), recipient.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.receiveMessage(message);
        
        // Print confirmation
        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        System.out.println("[" + time + "] You → " + recipient.getUsername() + ": " + content);
    }
    
    /**
     * Handle incoming message (called by RMI).
     */
    public void handleIncomingMessage(DirectMessage message) {
        String time = TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimestamp()));
        System.out.println("\n[" + time + "] " + message.getSenderUsername() + ": " + message.getContent());
        System.out.println("Vector Clock: " + message.getVectorClock());
        System.out.print("> ");
    }
}
