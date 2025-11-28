package p2p.common.model.message;

import p2p.common.model.MessageTopic;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Message for exchanging state digests (Cassandra-style gossip).
 * Includes leader liveness information for gossip-based failure detection.
 */
public final class GossipMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final String senderUsername;
    private final VectorClock vectorClock;
    // Leader liveness: groupId -> last seen timestamp
    private final Map<String, Long> leaderLastSeen;

    public GossipMessage(String messageId, String senderId, String senderUsername, String groupId, long timestamp,
            VectorClock vectorClock, Map<String, Long> leaderLastSeen) {
        super(messageId, senderId, timestamp, MessageTopic.GOSSIP);
        this.senderUsername = Objects.requireNonNull(senderUsername);
        this.groupId = Objects.requireNonNull(groupId);
        this.vectorClock = Objects.requireNonNull(vectorClock).clone();
        this.leaderLastSeen = leaderLastSeen != null ? Collections.unmodifiableMap(new HashMap<>(leaderLastSeen))
                : Collections.emptyMap();
    }

    public static GossipMessage create(User sender, String groupId, VectorClock clock) {
        return new GossipMessage(
                UUID.randomUUID().toString(),
                sender.getUserId(),
                sender.getUsername(),
                groupId,
                Instant.now().toEpochMilli(),
                clock,
                null);
    }

    public static GossipMessage create(User sender, String groupId, VectorClock clock,
            Map<String, Long> leaderLastSeen) {
        return new GossipMessage(
                UUID.randomUUID().toString(),
                sender.getUserId(),
                sender.getUsername(),
                groupId,
                Instant.now().toEpochMilli(),
                clock,
                leaderLastSeen);
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

    public Map<String, Long> getLeaderLastSeen() {
        return leaderLastSeen;
    }

    @Override
    public String toString() {
        return String.format("GossipMessage{id='%s', group='%s', from='%s', clock=%s, leaderLastSeen=%s}",
                messageId, groupId, senderUsername, vectorClock, leaderLastSeen);
    }
}
