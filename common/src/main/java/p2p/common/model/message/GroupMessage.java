package p2p.common.model.message;

import p2p.common.model.MessageType;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Message for group chat communication.
 */
public final class GroupMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final String senderUsername;
    private final String content;
    private final VectorClock vectorClock;

    public GroupMessage(String messageId, String senderId, String senderUsername,
                        String groupId, String content, long timestamp, VectorClock vectorClock) {
        super(messageId, senderId, timestamp, MessageType.GROUP);
        this.senderUsername = Objects.requireNonNull(senderUsername);
        this.groupId = Objects.requireNonNull(groupId);
        this.content = Objects.requireNonNull(content);
        this.vectorClock = Objects.requireNonNull(vectorClock).clone();
    }

    public static GroupMessage create(User sender, String groupId, String content, VectorClock clock) {
        return new GroupMessage(
            UUID.randomUUID().toString(),
            sender.getUserId(),
            sender.getUsername(),
            groupId,
            content,
            Instant.now().toEpochMilli(),
            clock
        );
    }

    public String getGroupId() {
        return groupId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getContent() {
        return content;
    }

    public VectorClock getVectorClock() {
        return vectorClock.clone();
    }

    @Override
    public String toString() {
        return String.format("GroupMessage{id='%s', group='%s', from='%s', content='%s'}",
            messageId, groupId, senderUsername, content);
    }
}
