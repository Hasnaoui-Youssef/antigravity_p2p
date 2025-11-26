package p2p.common.model;

import p2p.common.vectorclock.VectorClock;

import java.io.Serializable;
import java.util.*;

/**
 * Represents a group chat.
 * Immutable and serializable.
 */
public final class Group implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String groupId;
    private final String name;
    private final String leaderId;
    private final Set<User> members; // Members excluding the leader
    private final long epoch; // For leader election

    public Group(String groupId, String name, String leaderId, Set<User> members, long epoch) {
        this.groupId = Objects.requireNonNull(groupId);
        this.name = Objects.requireNonNull(name);
        this.leaderId = Objects.requireNonNull(leaderId);
        this.members = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(members)));
        this.epoch = epoch;
    }

    public static Group create(String name, User creator, List<User> initialMembers) {
        // Create set of members, excluding the creator (leader)
        Set<User> memberSet = new HashSet<>();
        if (initialMembers != null) {
            for (User user : initialMembers) {
                // Exclude creator from members - they're the leader
                if (!user.getUserId().equals(creator.getUserId())) {
                    memberSet.add(user);
                }
            }
        }
        return new Group(
            UUID.randomUUID().toString(),
            name,
            creator.getUserId(),
            memberSet,
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

    public Set<User> getMembers() {
        return members;
    }

    public long getEpoch() {
        return epoch;
    }

    public boolean isMember(String userId) {
        // Check if user is leader OR in members set
        return leaderId.equals(userId) || members.stream().anyMatch(u -> u.getUserId().equals(userId));
    }

    public Group withNewLeader(String newLeaderId, long newEpoch) {
        return new Group(groupId, name, newLeaderId, members, newEpoch);
    }

    public Group withAddedMember(User newMember) {
        // Don't add if already a member or if they're the leader
        if (isMember(newMember.getUserId()) || newMember.getUserId().equals(leaderId)) {
            return this;
        }
        Set<User> newMembers = new HashSet<>(members);
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
