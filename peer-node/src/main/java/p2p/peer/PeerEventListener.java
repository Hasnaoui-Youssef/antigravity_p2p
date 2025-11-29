package p2p.peer;

import p2p.common.model.User;
import p2p.common.model.message.GroupInvitationMessage;
import p2p.common.model.message.Message;

/**
 * Listener interface for peer events.
 * Decouples the business logic from the UI/Presentation layer.
 */
public interface PeerEventListener {

    /**
     * Called when a friend request is received.
     */
    void onFriendRequest(User requester);

    /**
     * Called when a friend request is accepted.
     */
    void onFriendRequestAccepted(User accepter);

    /**
     * Called when a message is received (Direct, Group, or Gossip).
     */
    void onMessageReceived(Message message);

    /**
     * Called when a group invitation is received.
     */
    void onGroupInvitation(GroupInvitationMessage request);

    /**
     * Called when a generic group event occurs (created, dissolved, member
     * joined/left).
     */
    void onGroupEvent(String groupId, String eventType, String message);

    /**
     * Called when a new leader is elected for a group.
     */
    void onLeaderElected(String groupId, String leaderId, long epoch);

    /**
     * Called when an error occurs.
     */
    void onError(String message, Throwable t);

    /**
     * Called for general log messages.
     */
    void onLog(String message);
}
