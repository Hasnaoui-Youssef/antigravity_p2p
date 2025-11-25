package p2p.common.model;

import p2p.common.vectorclock.VectorClock;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a message between peers with vector clock for ordering.
 */
public final class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String messageId;
    private final String senderId;
    private final String senderUsername;
    private final String receiverId;
    private final String content;
    private final long timestamp;
    private final VectorClock vectorClock;
    
    public Message(String messageId, String senderId, String senderUsername,
                   String receiverId, String content, long timestamp, VectorClock vectorClock) {
        this.messageId = Objects.requireNonNull(messageId);
        this.senderId = Objects.requireNonNull(senderId);
        this.senderUsername = Objects.requireNonNull(senderUsername);
        this.receiverId = Objects.requireNonNull(receiverId);
        this.content = Objects.requireNonNull(content);
        this.timestamp = timestamp;
        this.vectorClock = Objects.requireNonNull(vectorClock).clone();
    }
    
    public static Message create(User sender, String receiverId, String content, VectorClock clock) {
        return new Message(
            UUID.randomUUID().toString(),
            sender.getUserId(),
            sender.getUsername(),
            receiverId,
            content,
            Instant.now().toEpochMilli(),
            clock
        );
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getSenderId() {
        return senderId;
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
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public VectorClock getVectorClock() {
        return vectorClock.clone();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return messageId.equals(message.messageId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }
    
    @Override
    public String toString() {
        return String.format("Message{from='%s', to='%s', content='%s', vc=%s}",
                           senderUsername, receiverId, content, vectorClock);
    }
}
