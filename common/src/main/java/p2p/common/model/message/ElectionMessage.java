package p2p.common.model.message;

import p2p.common.model.MessageTopic;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Message for leader election (ZooKeeper-style).
 */
public final class ElectionMessage extends Message {
    private static final long serialVersionUID = 1L;

    public enum ElectionType {
        PROPOSAL,
        VOTE,
        RESULT
    }

    private final String groupId;
    private final ElectionType electionType;
    private final String candidateId;
    private final long epoch;

    public ElectionMessage(String messageId, String senderId, long timestamp,
            String groupId, ElectionType electionType, String candidateId, long epoch) {
        super(messageId, senderId, timestamp, MessageTopic.ELECTION);
        this.groupId = Objects.requireNonNull(groupId);
        this.electionType = Objects.requireNonNull(electionType);
        this.candidateId = Objects.requireNonNull(candidateId);
        this.epoch = epoch;
    }

    public static ElectionMessage create(String senderId, String groupId, ElectionType type, String candidateId,
            long epoch) {
        return new ElectionMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                groupId,
                type,
                candidateId,
                epoch);
    }

    public String getGroupId() {
        return groupId;
    }

    public ElectionType getElectionType() {
        return electionType;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public long getEpoch() {
        return epoch;
    }

    @Override
    public String toString() {
        return String.format("ElectionMessage{id='%s', group='%s', type=%s, candidate='%s', epoch=%d}",
                messageId, groupId, electionType, candidateId, epoch);
    }
}
