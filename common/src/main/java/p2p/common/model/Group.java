package p2p.common.model;

import p2p.common.vectorclock.VectorClock;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a group chat.
 * Immutable and serializable.
 */
public final class Group implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final String name;
    private final String leaderId;
    private final List<User> members;
    private final long epoch; // For leader election

    public Group(String groupId, String name, String leaderId, List<User> members, long epoch) {
        this.groupId = Objects.requireNonNull(groupId);
        this.name = Objects.requireNonNull(name);
        this.leaderId = Objects.requireNonNull(leaderId);
        this.members = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(members)));
        this.epoch = epoch;
    }

    public static Group create(String name, User creator, List<User> initialMembers) {
        List<User> allMembers = new ArrayList<>();
        allMembers.add(creator);
        if (initialMembers != null) {
            allMembers.addAll(initialMembers);
        }
        return new Group(
            UUID.randomUUID().toString(),
            name,
            creator.getUserId(),
            allMembers,
            0
        );
    }

    public String getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public List<User> getMembers() {
        return members;
    }

    public long getEpoch() {
        return epoch;
    }

    public boolean isMember(String userId) {
        return members.stream().anyMatch(u -> u.getUserId().equals(userId));
    }

    public Group withNewLeader(String newLeaderId, long newEpoch) {
        return new Group(groupId, name, newLeaderId, members, newEpoch);
    }

    public Group withAddedMember(User newMember) {
        if (isMember(newMember.getUserId())) {
            return this;
        }
        List<User> newMembers = new ArrayList<>(members);
        newMembers.add(newMember);
        return new Group(groupId, name, leaderId, newMembers, epoch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return groupId.equals(group.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId);
    }

    @Override
    public String toString() {
        return String.format("Group{id='%s', name='%s', leader='%s', members=%d, epoch=%d}",
            groupId, name, leaderId, members.size(), epoch);
    }
}
