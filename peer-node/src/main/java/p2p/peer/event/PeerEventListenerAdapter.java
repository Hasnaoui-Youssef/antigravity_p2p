package p2p.peer.event;

import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.common.model.message.ChatMessage;
import p2p.common.model.message.GroupInvitationMessage;
import p2p.common.model.message.GroupInvitationRequest;
import p2p.common.model.message.Message;
import p2p.common.vectorclock.VectorClock;

/**
 * Adapter class with empty default implementations for convenience.
 * Extend this class to only implement the methods you need.
 */
public class PeerEventListenerAdapter implements PeerEventListener {

    @Override
    public void onFriendRequestReceived(User requester) {
        // Default empty implementation
    }

    @Override
    public void onFriendRequestAccepted(User accepter) {
        // Default empty implementation
    }

    @Override
    public void onChatMessageReceived(ChatMessage message) {
        // Default empty implementation
    }

    @Override
    public void onChatMessageSent(ChatMessage message, boolean success) {
        // Default empty implementation
    }

    @Override
    public void onGroupInvitationReceived(GroupInvitationMessage invitation) {
        // Default empty implementation
    }

    @Override
    public void onGroupInvitationResponseReceived(GroupInvitationMessage response) {
        // Default empty implementation
    }

    @Override
    public void onGroupJoined(Group group) {
        // Default empty implementation
    }

    @Override
    public void onVectorClockUpdated(VectorClock clock) {
        // Default empty implementation
    }

    @Override
    public void onPeerConnected(User peer) {
        // Default empty implementation
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        // Default empty implementation
    }

    @Override
    public void onLeaderElected(String groupId, String leaderId) {
        // Default empty implementation
    }

    @Override
    public void onError(String context, Exception error) {
        // Default empty implementation
    }

    // ========== Legacy Methods (with implementations for backwards compatibility) ==========

    @Override
    public void onFriendRequest(User requester) {
        onFriendRequestReceived(requester);
    }

    @Override
    public void onMessageReceived(Message message) {
        if (message instanceof ChatMessage) {
            onChatMessageReceived((ChatMessage) message);
        }
    }

    @Override
    public void onGroupInvitation(GroupInvitationRequest request) {
        // Default empty implementation
    }

    @Override
    public void onGroupEvent(String groupId, String eventType, String message) {
        // Default empty implementation
    }

    @Override
    public void onLeaderElected(String groupId, String leaderId, long epoch) {
        onLeaderElected(groupId, leaderId);
    }

    @Override
    public void onError(String message, Throwable t) {
        onError(message, t instanceof Exception ? (Exception) t : new RuntimeException(t));
    }

    @Override
    public void onLog(String message) {
        // Default empty implementation
    }
}
