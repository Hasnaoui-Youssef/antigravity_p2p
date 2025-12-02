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
public record Group(String groupId, String name, User leader, Set<User> members, Set<User> pendingMembers, Set<User> rejectedMembers,
                    long epoch) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public Group(String groupId, String name, User leader, Set<User> members, Set<User> pendingMembers, Set<User> rejectedMembers, long epoch) {
        this.groupId = Objects.requireNonNull(groupId);
        this.name = Objects.requireNonNull(name);
        this.leader = Objects.requireNonNull(leader);
        this.members = Set.copyOf(Objects.requireNonNull(members));
        this.pendingMembers = Set.copyOf(Objects.requireNonNull(pendingMembers));
        this.rejectedMembers = Set.copyOf(Objects.requireNonNull(rejectedMembers));
        this.epoch = epoch;
    }

    public static Group create(String name, User creator, List<User> initialMembers) {
        // Create set of pending members, excluding the creator (leader)
        Set<User> pendingSet = new HashSet<>();
        if (initialMembers != null) {
            for (User user : initialMembers) {
                // Exclude creator from members - they're the leader
                if (!user.userId().equals(creator.userId())) {
                    pendingSet.add(user);
                }
            }
        }
        return new Group(
                UUID.randomUUID().toString(),
                name,
                creator,
                Collections.emptySet(),
                pendingSet,
                Collections.emptySet(),
                0);
    }

    public boolean isMember(String userId) {
        // Check if user is leader OR in members set
        return leader.userId().equals(userId) || members.stream().anyMatch(u -> u.userId().equals(userId));
    }

    public boolean isPending(String userId) {
        return pendingMembers.stream().anyMatch(u -> u.userId().equals(userId));
    }

    public boolean isRejected(String userId) {
        return rejectedMembers.stream().anyMatch(u -> u.userId().equals(userId));
    }

    public Group withNewLeader(User newLeader, long newEpoch) {
        return new Group(groupId, name, newLeader, members, pendingMembers, rejectedMembers, newEpoch);
    }

    public Group withAddedMember(User newMember) {
        // Don't add if already a member or if they're the leader
        if (isMember(newMember.userId()) || newMember.userId().equals(leader.userId())) {
            return this;
        }
        Set<User> newMembers = new HashSet<>(members);
        newMembers.add(newMember);
        
        Set<User> newPending = new HashSet<>(pendingMembers);
        newPending.remove(newMember);

        return new Group(groupId, name, leader, newMembers, newPending, rejectedMembers, epoch);
    }

    public Group withRejectedMember(User rejectedMember) {
        if (isMember(rejectedMember.userId()) || rejectedMember.userId().equals(leader.userId())) {
            return this;
        }
        Set<User> newRejected = new HashSet<>(rejectedMembers);
        newRejected.add(rejectedMember);

        Set<User> newPending = new HashSet<>(pendingMembers);
        newPending.remove(rejectedMember);

        return new Group(groupId, name, leader, members, newPending, newRejected, epoch);
    }

    public Group withRemovedMember(User memberToRemove) {
        // Don't remove if not a member and not the leader (leader removal is handled by election)
        if (!isMember(memberToRemove.userId()) && !memberToRemove.userId().equals(leader.userId())) {
            return this;
        }
        Set<User> newMembers = new HashSet<>(members);
        newMembers.remove(memberToRemove);
        return new Group(groupId, name, leader, newMembers, pendingMembers, rejectedMembers, epoch);
    }

    /**
     * Returns all users associated with the group (Leader + Active + Pending).
     * Useful for broadcasting election updates or group changes.
     */
    public Set<User> allMembers() {
        Set<User> all = new HashSet<>();
        all.add(leader);
        all.addAll(members);
        all.addAll(pendingMembers);
        return all;
    }

    /**
     * Returns active members (Leader + Active Members).
     * Useful for chat messages.
     */
    public Set<User> activeMembers() {
        Set<User> active = new HashSet<>();
        active.add(leader);
        active.addAll(members);
        return active;
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
        return String.format("Group{id='%s', name='%s', leader='%s', members=%d, pending=%d, rejected=%d, epoch=%d}",
                groupId, name, leader.username(), members.size(), pendingMembers.size(), rejectedMembers.size(), epoch);
    }
}
