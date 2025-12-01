package p2p.common.model.message;

import p2p.common.model.MessageTopic;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class FriendMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final User sender;

    public enum SubTopic {
        FRIEND_REQUEST,
        FRIEND_ACCEPT,
        FRIEND_REJECT
    }

    private final SubTopic friendMessageType;

    private FriendMessage(String messageId, String senderId, long timestamp, User sender,
            SubTopic messageType, VectorClock vectorClock) {
        super(messageId, senderId, timestamp, MessageTopic.FRIEND_MESSAGE, vectorClock);
        this.sender = Objects.requireNonNull(sender);
        this.friendMessageType = Objects.requireNonNull(messageType);
    }

    public static FriendMessage create(User sender, SubTopic messageType, VectorClock vectorClock) {
        return new FriendMessage(
                UUID.randomUUID().toString(),
                sender.getUserId(),
                Instant.now().toEpochMilli(),
                sender,
                messageType,
                vectorClock);
    }

    public User getSender() {
        return sender;
    }

    public SubTopic getFriendMessageType() {
        return friendMessageType;
    }

    @Override
    public String toString() {
        return String.format("FriendMessage{id='%s', sender='%s', messageType='%s'}", messageId, sender.getUsername(),
                friendMessageType);
    }

}
