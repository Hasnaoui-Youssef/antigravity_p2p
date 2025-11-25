package p2p.common.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Response containing missing messages.
 */
public final class SyncResponse extends Message {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final List<Message> missingMessages;

    public SyncResponse(String messageId, String senderId, long timestamp, String groupId, List<Message> missingMessages) {
        super(messageId, senderId, timestamp, MessageType.SYNC_RESPONSE);
        this.groupId = Objects.requireNonNull(groupId);
        this.missingMessages = Collections.unmodifiableList(Objects.requireNonNull(missingMessages));
    }

    public static SyncResponse create(String senderId, String groupId, List<Message> missingMessages) {
        return new SyncResponse(
            UUID.randomUUID().toString(),
            senderId,
            Instant.now().toEpochMilli(),
            groupId,
            missingMessages
        );
    }

    public String getGroupId() {
        return groupId;
    }

    public List<Message> getMissingMessages() {
        return missingMessages;
    }

    @Override
    public String toString() {
        return String.format("SyncResponse{id='%s', sender='%s', group='%s', count=%d}",
            messageId, senderId, groupId, missingMessages.size());
    }
}
