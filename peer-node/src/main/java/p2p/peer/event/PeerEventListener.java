package p2p.peer.event;

import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.common.model.message.ChatMessage;
import p2p.common.model.message.GroupInvitationMessage;
import p2p.common.model.message.GroupInvitationRequest;
import p2p.common.model.message.Message;
import p2p.common.vectorclock.VectorClock;

/**
 * Comprehensive event listener interface to decouple UI/view from core logic.
 * This enables future migration to OpenGL/native APIs.
 */
public interface PeerEventListener {

    // ========== Friend Events ==========
    
    /**
     * Called when a friend request is received.
     */
    void onFriendRequestReceived(User requester);

    /**
     * Called when a friend request is accepted.
     */
    void onFriendRequestAccepted(User accepter);

    // ========== Chat Message Events ==========
    
    /**
     * Called when a chat message is received.
     */
    void onChatMessageReceived(ChatMessage message);

    /**
     * Called when a chat message is sent.
     * @param message The message that was sent
     * @param success Whether the send was successful
     */
    void onChatMessageSent(ChatMessage message, boolean success);

    // ========== Group Invitation Events ==========
    
    /**
     * Called when a group invitation is received.
     */
    void onGroupInvitationReceived(GroupInvitationMessage invitation);

    /**
     * Called when a group invitation response is received.
     */
    void onGroupInvitationResponseReceived(GroupInvitationMessage response);

    /**
     * Called when the user joins a group.
     */
    void onGroupJoined(Group group);

    // ========== Sync/System Events ==========
    
    /**
     * Called when the vector clock is updated.
     */
    void onVectorClockUpdated(VectorClock clock);

    /**
     * Called when a peer connects.
     */
    void onPeerConnected(User peer);

    /**
     * Called when a peer disconnects.
     */
    void onPeerDisconnected(String peerId);

    /**
     * Called when a new leader is elected for a group.
     */
    void onLeaderElected(String groupId, String leaderId);

    // ========== Error Events ==========
    
    /**
     * Called when an error occurs.
     */
    void onError(String context, Exception error);

    // ========== Legacy Methods (for backwards compatibility) ==========
    
    /**
     * @deprecated Use onFriendRequestReceived instead
     */
    @Deprecated
    default void onFriendRequest(User requester) {
        onFriendRequestReceived(requester);
    }

    /**
     * @deprecated Use onChatMessageReceived for chat messages
     */
    @Deprecated
    default void onMessageReceived(Message message) {
        if (message instanceof ChatMessage) {
            onChatMessageReceived((ChatMessage) message);
        }
    }

    /**
     * @deprecated Use onGroupInvitationReceived instead
     */
    @Deprecated
    default void onGroupInvitation(GroupInvitationRequest request) {
        // Convert to new message type for handling
    }

    /**
     * @deprecated Use onGroupJoined or more specific event methods
     */
    @Deprecated
    default void onGroupEvent(String groupId, String eventType, String message) {
        // Legacy support
    }

    /**
     * @deprecated Use onLeaderElected(String, String) instead
     */
    @Deprecated
    default void onLeaderElected(String groupId, String leaderId, long epoch) {
        onLeaderElected(groupId, leaderId);
    }

    /**
     * @deprecated Use onError(String, Exception) instead
     */
    @Deprecated
    default void onError(String message, Throwable t) {
        onError(message, t instanceof Exception ? (Exception) t : new RuntimeException(t));
    }

    /**
     * @deprecated Use more specific event methods
     */
    @Deprecated
    default void onLog(String message) {
        // Legacy support - no-op in new interface
    }
}
