package p2p.common.model.message;

import p2p.common.model.MessageTopic;

import java.io.Serializable;
import java.util.Objects;

/**
 * Abstract base class for all P2P messages.
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 2L;

    protected final String messageId;
    protected final String senderId;
    protected final long timestamp;
    protected final MessageTopic topic;

    protected Message(String messageId, String senderId, long timestamp, MessageTopic type) {
        this.messageId = Objects.requireNonNull(messageId);
        this.senderId = Objects.requireNonNull(senderId);
        this.timestamp = timestamp;
        this.topic = Objects.requireNonNull(type);
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
