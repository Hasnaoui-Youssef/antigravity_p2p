package p2p.common.model.message;

import p2p.common.model.MessageTopic;
import p2p.common.vectorclock.VectorClock;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Abstract base class for all P2P messages.
 */
public abstract class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    protected final String messageId;
    protected final String senderId;
    protected final long timestamp;
    protected final MessageTopic topic;
    protected final VectorClock vectorClock;

    protected Message(String messageId, String senderId, long timestamp, MessageTopic type) {
        this(messageId, senderId, timestamp, type, null);
    }

    protected Message(String messageId, String senderId, long timestamp, MessageTopic type, VectorClock vectorClock) {
        this.messageId = Objects.requireNonNull(messageId);
        this.senderId = Objects.requireNonNull(senderId);
        this.timestamp = timestamp;
        this.topic = Objects.requireNonNull(type);
        this.vectorClock = vectorClock != null ? vectorClock.clone() : null;
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

    public MessageTopic getTopic() {
        return topic;
    }

    /**
     * Gets the vector clock associated with this message.
     *
     * @return a defensive copy of the vector clock, or null if not set
     */
    public VectorClock getVectorClock() {
        return vectorClock != null ? vectorClock.clone() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Message message = (Message) o;
        return messageId.equals(message.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }

    @Override
    public String toString() {
        return String.format("Message{id='%s', topic=%s, sender='%s'}", messageId, topic, senderId);
    }
}
