package p2p.peer.peerui.model;

/**
 * Represents a conversation context (either direct message or group chat).
 *
 * @param type        The type of conversation (DIRECT or GROUP)
 * @param id          The unique identifier (userId for direct, groupId for
 *                    group)
 * @param displayName The name to display in the UI
 */
public record Conversation(ConversationType type, String id, String displayName) {

    /**
     * Creates a direct message conversation.
     */
    public static Conversation direct(String userId, String username) {
        return new Conversation(ConversationType.DIRECT, userId, username);
    }

    /**
     * Creates a group conversation.
     */
    public static Conversation group(String groupId, String groupName) {
        return new Conversation(ConversationType.GROUP, groupId, groupName);
    }

    public boolean isDirect() {
        return type == ConversationType.DIRECT;
    }

    public boolean isGroup() {
        return type == ConversationType.GROUP;
    }
}
