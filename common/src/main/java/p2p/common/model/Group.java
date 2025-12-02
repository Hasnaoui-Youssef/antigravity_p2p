package p2p.common.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a group chat.
 * Immutable and serializable.
 *
 * @param members Members excluding the leader
 * @param epoch   For leader election
 */
public record Group(String groupId, String name, User leader, Set<User> members,
                    long epoch) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public Group(String groupId, String name, User leader, Set<User> members, long epoch) {
        this.groupId = Objects.requireNonNull(groupId);
        this.name = Objects.requireNonNull(name);
        this.leader = Objects.requireNonNull(leader);
        this.members = Set.copyOf(Objects.requireNonNull(members));
        this.epoch = epoch;
    }

    public static Group create(String name, User creator, List<User> initialMembers) {
        // Create set of members, excluding the creator (leader)
        Set<User> memberSet = new HashSet<>();
        if (initialMembers != null) {
            for (User user : initialMembers) {
                // Exclude creator from members - they're the leader
                if (!user.userId().equals(creator.userId())) {
                    memberSet.add(user);
                }
            }
        }
        return new Group(
                UUID.randomUUID().toString(),
                name,
                creator,
                memberSet,
                0);
    }

    public boolean isMember(String userId) {
        // Check if user is leader OR in members set
        return leader.userId().equals(userId) || members.stream().anyMatch(u -> u.userId().equals(userId));
    }

    public Group withNewLeader(User newLeader, long newEpoch) {
        return new Group(groupId, name, newLeader, members, newEpoch);
    }

    public Group withAddedMember(User newMember) {
        // Don't add if already a member or if they're the leader
        if (isMember(newMember.userId()) || newMember.userId().equals(leader.userId())) {
            return this;
        }
        Set<User> newMembers = new HashSet<>(members);
        newMembers.add(newMember);
        return new Group(groupId, name, leader, newMembers, epoch);
    }

    public Group withRemovedMember(User newMember) {
        // Don't add if already a member or if they're the leader
        if (!isMember(newMember.userId()) && !newMember.userId().equals(leader.userId())) {
            return this;
        }
        Set<User> newMembers = new HashSet<>(members);
        newMembers.remove(newMember);
        return new Group(groupId, name, leader, newMembers, epoch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
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
                groupId, name, leader.username(), members.size(), epoch);
    }
}
