package p2p.common.model.message;

import p2p.common.model.MessageType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Response to a group invitation.
 */
public final class GroupInvitationResponse extends Message {
    private static final long serialVersionUID = 1L;

    public enum Status {
        ACCEPTED,
        REJECTED
    }

    private final String groupId;
    private final Status status;

    public GroupInvitationResponse(String messageId, String senderId, long timestamp, 
                                  String groupId, Status status) {
        super(messageId, senderId, timestamp, MessageType.INVITATION_RESPONSE);
        this.groupId = Objects.requireNonNull(groupId);
        this.status = Objects.requireNonNull(status);
    }

    public static GroupInvitationResponse create(String senderId, String groupId, Status status) {
        return new GroupInvitationResponse(
            UUID.randomUUID().toString(),
            senderId,
            Instant.now().toEpochMilli(),
            groupId,
            status
        );
    }

    public String getGroupId() {
        return groupId;
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("GroupInvitationResponse{id='%s', sender='%s', group='%s', status=%s}",
            messageId, senderId, groupId, status);
    }
}
