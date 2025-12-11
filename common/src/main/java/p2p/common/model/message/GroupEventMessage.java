package p2p.common.model.message;

import p2p.common.model.Group;
import p2p.common.model.GroupEvent;
import p2p.common.model.MessageTopic;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class GroupEventMessage extends Message{

    @Serial
    private static final long serialVersionUID = 1L;
    private final Group group;
    private final GroupEvent groupEvent;
    private final User affectedUser;

    private GroupEventMessage(String messageId, String senderId, long timestamp,
                              GroupEvent groupEvent, Group group, VectorClock vectorClock, User user) {
        super(messageId, senderId,timestamp, MessageTopic.GROUP_EVENT, vectorClock);
        this.group = group;
        this.groupEvent = groupEvent;
        this.affectedUser = user;

    }

    public static GroupEventMessage groupCreatedMessage(String senderId,
                               Group group, VectorClock vectorClock) {
        return new GroupEventMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupEvent.CREATED,
                group,
                vectorClock,
                null
        );
    }
    public static GroupEventMessage groupDissolvedMessage(String senderId,
                                                        Group group, VectorClock vectorClock) {
        return new GroupEventMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupEvent.DISSOLVED,
                group,
                vectorClock,
                null
        );
    }

    public static GroupEventMessage userJoinedMessage(String senderId,
                                                          Group group, VectorClock vectorClock, User user) {
        return new GroupEventMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupEvent.USER_JOINED,
                group,
                vectorClock,
                Objects.requireNonNull(user)
        );
    }
    public static GroupEventMessage userLeftMessage(String senderId,
                                                      Group group, VectorClock vectorClock, User user) {
        return new GroupEventMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupEvent.USER_LEFT,
                group,
                vectorClock,
                Objects.requireNonNull(user)
        );
    }
    public static GroupEventMessage userRejectedMessage(String senderId,
                                                    Group group, VectorClock vectorClock, User user) {
        return new GroupEventMessage(
                UUID.randomUUID().toString(),
                senderId,
                Instant.now().toEpochMilli(),
                GroupEvent.USER_REJECTED,
                group,
                vectorClock,
                Objects.requireNonNull(user)
        );
    }

    public Group getGroup() {
        return group;
    }

    public GroupEvent getEvent() {
        return groupEvent;
    }
    public User getAffectedUser() {
        return affectedUser;
    }

    @Override
    public String toString() {
        return switch (groupEvent) {
            case CREATED -> String.format("GroupEventMessage{id='%s', type=CREATED, sender='%s', group='%s', members=%d}",
                    messageId, senderId, group.name(), group.members().size());
            case DISSOLVED -> String.format("GroupEventMessage{id='%s', type=DISSOLVED, sender='%s', group='%s'}",
                    messageId, senderId, group.name());
            case USER_JOINED -> String.format("GroupEventMessage{id='%s', type=USER_JOINED, sender='%s', group='%s'}",
                    messageId, senderId, group.name());
            case USER_LEFT -> String.format("GroupEventMessage{id='%s', type=USER_LEFT, sender='%s', group='%s'}",
                    messageId, senderId, group.name());
            case USER_REJECTED -> String.format("GroupEventMessage{id='%s', type=USER_REJECTED, sender='%s', group='%s'}",
                    messageId, senderId, group.name());
        };

    }
}
