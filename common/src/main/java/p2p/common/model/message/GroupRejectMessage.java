package p2p.common.model.message;

import p2p.common.model.MessageTopic;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Message sent to reject a group invitation.
 */
public final class GroupRejectMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String groupId;

    public GroupRejectMessage(String messageId, String senderId, long timestamp, String groupId) {
        super(messageId, senderId, timestamp, MessageTopic.GROUP_REJECT);
        this.groupId = Objects.requireNonNull(groupId);
    }

    public static GroupRejectMessage create(String senderId, String groupId) {
        return new GroupRejectMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                groupId);
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public String toString() {
        return String.format("GroupRejectMessage{id='%s', sender='%s', group='%s'}", messageId, senderId, groupId);
    }
}
