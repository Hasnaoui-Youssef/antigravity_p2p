package p2p.common.model.message;

import p2p.common.model.MessageTopic;
import p2p.common.vectorclock.VectorClock;

import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Request to synchronize missing messages for a group.
 */
public final class SyncRequest extends Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String groupId;

    public SyncRequest(String messageId, String senderId, long timestamp, String groupId, VectorClock lastKnownState) {
        super(messageId, senderId, timestamp, MessageTopic.SYNC_REQUEST, lastKnownState);
        this.groupId = Objects.requireNonNull(groupId);
    }

    public static SyncRequest create(String senderId, String groupId, VectorClock lastKnownState) {
        return new SyncRequest(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                groupId,
                lastKnownState);
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public String toString() {
        return String.format("SyncRequest{id='%s', sender='%s', group='%s', state=%s}",
                messageId, senderId, groupId, vectorClock);
    }
}
