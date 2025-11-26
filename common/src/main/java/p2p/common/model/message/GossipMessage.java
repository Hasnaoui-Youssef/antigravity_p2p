package p2p.common.model.message;

import p2p.common.model.MessageType;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Message for exchanging state digests (Cassandra-style gossip).
 */
public final class GossipMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final String senderUsername;
    private final VectorClock vectorClock;

    public GossipMessage(String messageId, String senderId, String senderUsername, String groupId, long timestamp, VectorClock vectorClock) {
        super(messageId, senderId, timestamp, MessageType.GOSSIP);
        this.senderUsername = Objects.requireNonNull(senderUsername);
        this.groupId = Objects.requireNonNull(groupId);
        this.vectorClock = Objects.requireNonNull(vectorClock).clone();
    }

    public static GossipMessage create(User sender, String groupId, VectorClock clock) {
        return new GossipMessage(
            UUID.randomUUID().toString(),
            sender.getUserId(),
            sender.getUsername(),
            groupId,
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

    public VectorClock getVectorClock() {
        return vectorClock.clone();
    }

    @Override
    public String toString() {
        return String.format("GossipMessage{id='%s', group='%s', from='%s', clock=%s}",
            messageId, groupId, senderUsername, vectorClock);
    }
}
