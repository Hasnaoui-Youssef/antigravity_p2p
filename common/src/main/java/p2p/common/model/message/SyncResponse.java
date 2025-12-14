package p2p.common.model.message;

import p2p.common.model.MessageTopic;
import p2p.common.vectorclock.VectorClock;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Response containing missing messages.
 */
public final class SyncResponse extends Message {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final String groupId;
    private final List<ChatMessage> missingMessages;

    public SyncResponse(String messageId, String senderId, long timestamp, String requestId, String groupId,
                        List<ChatMessage> missingMessages, VectorClock vectorClock) {
        super(messageId, senderId, timestamp, MessageTopic.SYNC_RESPONSE, vectorClock);
        this.requestId = Objects.requireNonNull(requestId);
        this.groupId = Objects.requireNonNull(groupId);
        this.missingMessages = List.copyOf(Objects.requireNonNull(missingMessages));
    }

    public static SyncResponse create(String senderId, String requestId, String groupId,
                                      List<ChatMessage> missingMessages, VectorClock vectorClock) {
        return new SyncResponse(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                requestId,
                groupId,
                missingMessages,
                vectorClock);
    }

    public String getRequestId() {
        return requestId;
    }

    public String getGroupId() {
        return groupId;
    }

    public List<ChatMessage> getMissingMessages() {
        return missingMessages;
    }

    @Override
    public String toString() {
        return String.format("SyncResponse{id='%s', reqId='%s', sender='%s', group='%s', count=%d}",
                messageId, requestId, senderId, groupId, missingMessages.size());
    }
}
