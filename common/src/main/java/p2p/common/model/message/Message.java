package p2p.common.model.message;

import p2p.common.model.MessageType;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for all P2P messages.
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 2L;
    
    protected final String messageId;
    protected final String senderId;
    protected final long timestamp;
    protected final MessageType type;
    
    protected Message(String messageId, String senderId, long timestamp, MessageType type) {
        this.messageId = Objects.requireNonNull(messageId);
        this.senderId = Objects.requireNonNull(senderId);
        this.timestamp = timestamp;
        this.type = Objects.requireNonNull(type);
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public MessageType getType() {
        return type;
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
        return String.format("Message{id='%s', type=%s, sender='%s'}", messageId, type, senderId);
    }
}
