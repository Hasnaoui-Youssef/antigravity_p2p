package p2p.common.model.message;

import p2p.common.model.Group;
import p2p.common.model.MessageTopic;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Unified message class for group invitation requests and responses.
 * Uses the subtopic pattern to distinguish between message types.
 */
public final class GroupInvitationMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final GroupInvitationSubtopic subtopic;
    private final String groupId;
    private final String groupName;
    private final List<User> potentialMembers;  // Only populated for REQUEST

    public GroupInvitationMessage(String messageId, String senderId, long timestamp,
            GroupInvitationSubtopic subtopic, String groupId, String groupName,
            List<User> potentialMembers, VectorClock vectorClock) {
        super(messageId, senderId, timestamp, MessageTopic.GROUP_INVITATION, vectorClock);
        this.subtopic = Objects.requireNonNull(subtopic);
        this.groupId = Objects.requireNonNull(groupId);
        this.groupName = groupName;  // May be null for responses
        this.potentialMembers = potentialMembers != null 
            ? Collections.unmodifiableList(potentialMembers) 
            : null;
    }

    /**
     * Factory method for creating an invitation request.
     */
    public static GroupInvitationMessage createRequest(String senderId, String groupId, 
            String groupName, List<User> potentialMembers) {
        return new GroupInvitationMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupInvitationSubtopic.REQUEST,
                groupId,
                groupName,
                potentialMembers,
                null);
    }

    /**
     * Factory method for creating an acceptance response.
     */
    public static GroupInvitationMessage createAccept(String senderId, String groupId) {
        return new GroupInvitationMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupInvitationSubtopic.ACCEPT,
                groupId,
                null,
                null,
                null);
    }

    /**
     * Factory method for creating a rejection response.
     */
    public static GroupInvitationMessage createReject(String senderId, String groupId) {
        return new GroupInvitationMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupInvitationSubtopic.REJECT,
                groupId,
                null,
                null,
                null);
    }

    public GroupInvitationSubtopic getSubtopic() {
        return subtopic;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    /**
     * Gets the potential members for invitation requests.
     * @return list of potential members, or null if this is a response
     */
    public List<User> getPotentialMembers() {
        return potentialMembers;
    }

    /**
     * Checks if this invitation was accepted.
     */
    public boolean isAccepted() {
        return subtopic == GroupInvitationSubtopic.ACCEPT;
    }

    /**
     * Checks if this invitation was rejected.
     */
    public boolean isRejected() {
        return subtopic == GroupInvitationSubtopic.REJECT;
    }

    /**
     * Checks if this is an invitation request.
     */
    public boolean isRequest() {
        return subtopic == GroupInvitationSubtopic.REQUEST;
    }

    @Override
    public String toString() {
        return switch (subtopic) {
            case REQUEST -> String.format("GroupInvitationMessage{id='%s', type=REQUEST, sender='%s', group='%s', members=%d}",
                    messageId, senderId, groupName, potentialMembers != null ? potentialMembers.size() : 0);
            case ACCEPT -> String.format("GroupInvitationMessage{id='%s', type=ACCEPT, sender='%s', group='%s'}",
                    messageId, senderId, groupId);
            case REJECT -> String.format("GroupInvitationMessage{id='%s', type=REJECT, sender='%s', group='%s'}",
                    messageId, senderId, groupId);
        };
    }
}
