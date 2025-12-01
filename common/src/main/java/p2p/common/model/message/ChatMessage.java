package p2p.common.model.message;

import p2p.common.model.MessageTopic;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Unified message class for both direct and group chat messages.
 * Uses the subtopic pattern to distinguish between message types.
 */
public final class ChatMessage extends Message {
    private static final long serialVersionUID = 1L;

    public enum ChatSubtopic {
        DIRECT, // Direct message to a user
        GROUP // Message to a group
    }

    private final ChatSubtopic subtopic;
    private final String targetId; // userId for DIRECT, groupId for GROUP
    private final String content;
    private final String senderUsername;

    public ChatMessage(String messageId, String senderId, String senderUsername,
            String targetId, String content, long timestamp,
            ChatSubtopic subtopic, VectorClock vectorClock) {
        super(messageId, senderId, timestamp, MessageTopic.CHAT, vectorClock);
        this.subtopic = Objects.requireNonNull(subtopic);
        this.targetId = Objects.requireNonNull(targetId);
        this.content = Objects.requireNonNull(content);
        this.senderUsername = Objects.requireNonNull(senderUsername);
    }

    /**
     * Factory method for creating a direct message.
     */
    public static ChatMessage createDirect(User sender, String receiverId, String content, VectorClock clock) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                sender.getUserId(),
                sender.getUsername(),
                receiverId,
                content,
                Instant.now().toEpochMilli(),
                ChatSubtopic.DIRECT,
                clock);
    }

    /**
     * Factory method for creating a group message.
     */
    public static ChatMessage createGroup(User sender, String groupId, String content, VectorClock clock) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                sender.getUserId(),
                sender.getUsername(),
                groupId,
                content,
                Instant.now().toEpochMilli(),
                ChatSubtopic.GROUP,
                clock);
    }

    public ChatSubtopic getSubtopic() {
        return subtopic;
    }

    public String getTargetId() {
        return targetId;
    }

    /**
     * Gets the receiver ID for direct messages.
     * 
     * @return the receiver user ID
     * @throws IllegalStateException if this is not a direct message
     */
    public String getReceiverId() {
        if (subtopic != ChatSubtopic.DIRECT) {
            throw new IllegalStateException("getReceiverId() is only valid for DIRECT messages");
        }
        return targetId;
    }

    /**
     * Gets the group ID for group messages.
     * 
     * @return the group ID
     * @throws IllegalStateException if this is not a group message
     */
    public String getGroupId() {
        if (subtopic != ChatSubtopic.GROUP) {
            throw new IllegalStateException("getGroupId() is only valid for GROUP messages");
        }
        return targetId;
    }

    public String getContent() {
        return content;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    @Override
    public VectorClock getVectorClock() {
        VectorClock clock = super.getVectorClock();
        return clock != null ? clock.clone() : null;
    }

    @Override
    public String toString() {
        if (subtopic == ChatSubtopic.DIRECT) {
            return String.format("ChatMessage{id='%s', type=DIRECT, from='%s', to='%s', content='%s'}",
                    messageId, senderUsername, targetId, content);
        } else {
            return String.format("ChatMessage{id='%s', type=GROUP, from='%s', group='%s', content='%s'}",
                    messageId, senderUsername, targetId, content);
        }
    }
}
