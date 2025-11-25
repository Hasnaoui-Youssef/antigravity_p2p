package p2p.common.model;

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

    // Map<GroupId, MaxMessageTimestamp>
    private final Map<String, Long> groupDigests;

    public GossipMessage(String messageId, String senderId, long timestamp, Map<String, Long> groupDigests) {
        super(messageId, senderId, timestamp, MessageType.GOSSIP);
        this.groupDigests = Collections.unmodifiableMap(Objects.requireNonNull(groupDigests));
    }

    public static GossipMessage create(String senderId, Map<String, Long> groupDigests) {
        return new GossipMessage(
            UUID.randomUUID().toString(),
            senderId,
            Instant.now().toEpochMilli(),
            groupDigests
        );
    }

    public Map<String, Long> getGroupDigests() {
        return groupDigests;
    }

    @Override
    public String toString() {
        return String.format("GossipMessage{id='%s', sender='%s', digests=%s}",
            messageId, senderId, groupDigests);
    }
}
