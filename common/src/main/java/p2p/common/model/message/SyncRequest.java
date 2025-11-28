package p2p.common.model.message;

import p2p.common.model.MessageTopic;
import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Request to synchronize missing messages for a group.
 */
public final class SyncRequest extends Message {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final VectorClock lastKnownState;

    public SyncRequest(String messageId, String senderId, long timestamp, String groupId, VectorClock lastKnownState) {
        super(messageId, senderId, timestamp, MessageTopic.SYNC_REQUEST);
        this.groupId = Objects.requireNonNull(groupId);
        this.lastKnownState = Objects.requireNonNull(lastKnownState).clone();
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

    public VectorClock getLastKnownState() {
        return lastKnownState.clone();
    }

    @Override
    public String toString() {
        return String.format("SyncRequest{id='%s', sender='%s', group='%s', state=%s}",
                messageId, senderId, groupId, lastKnownState);
    }
}
