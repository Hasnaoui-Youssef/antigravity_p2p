package p2p.common.model;

import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Message for direct one-to-one communication.
 */
public final class DirectMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String senderUsername;
    private final String receiverId;
    private final String content;
    private final VectorClock vectorClock;

    public DirectMessage(String messageId, String senderId, String senderUsername,
                         String receiverId, String content, long timestamp, VectorClock vectorClock) {
        super(messageId, senderId, timestamp, MessageType.DIRECT);
        this.senderUsername = Objects.requireNonNull(senderUsername);
        this.receiverId = Objects.requireNonNull(receiverId);
        this.content = Objects.requireNonNull(content);
        this.vectorClock = Objects.requireNonNull(vectorClock).clone();
    }

    public static DirectMessage create(User sender, String receiverId, String content, VectorClock clock) {
        return new DirectMessage(
            UUID.randomUUID().toString(),
            sender.getUserId(),
            sender.getUsername(),
            receiverId,
            content,
            Instant.now().toEpochMilli(),
            clock
        );
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getContent() {
        return content;
    }

    public VectorClock getVectorClock() {
        return vectorClock.clone();
    }

    @Override
    public String toString() {
        return String.format("DirectMessage{id='%s', from='%s', to='%s', content='%s'}",
            messageId, senderUsername, receiverId, content);
    }
}
