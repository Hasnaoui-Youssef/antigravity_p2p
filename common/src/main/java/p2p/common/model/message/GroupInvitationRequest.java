package p2p.common.model.message;

import p2p.common.model.MessageType;
import p2p.common.model.User;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Message sent to invite a user to a group.
 */
public final class GroupInvitationRequest extends Message {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final String groupName;
    private final List<User> potentialMembers;

    public GroupInvitationRequest(String messageId, String senderId, long timestamp, 
                                 String groupId, String groupName, List<User> potentialMembers) {
        super(messageId, senderId, timestamp, MessageType.INVITATION_REQUEST);
        this.groupId = Objects.requireNonNull(groupId);
        this.groupName = Objects.requireNonNull(groupName);
        this.potentialMembers = Collections.unmodifiableList(Objects.requireNonNull(potentialMembers));
    }

    public static GroupInvitationRequest create(String senderId, String groupId, String groupName, List<User> potentialMembers) {
        return new GroupInvitationRequest(
            UUID.randomUUID().toString(),
            senderId,
            Instant.now().toEpochMilli(),
            groupId,
            groupName,
            potentialMembers
        );
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }
    
    public List<User> getPotentialMembers() {
        return potentialMembers;
    }

    @Override
    public String toString() {
        return String.format("GroupInvitationRequest{id='%s', sender='%s', group='%s', members=%d}",
            messageId, senderId, groupName, potentialMembers.size());
    }
}
