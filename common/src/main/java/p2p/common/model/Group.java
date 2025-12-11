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
public record Group(String groupId, String name, User leader, Set<User> members, Set<User> pendingMembers, Set<String> rejectedMembers,
                    long epoch) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public Group(String groupId, String name, User leader, Set<User> members, Set<User> pendingMembers, Set<String> rejectedMembers, long epoch) {
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
        return members.stream().anyMatch(u -> u.userId().equals(userId));
    }

    public boolean isMember(User user) {
        // Check if user is leader OR in members set
        return members.stream().anyMatch(u -> u.userId().equals(user.userId()));
    }

    public boolean isLeader(String userId) {
        return leader.userId().equals(userId);
    }

    public boolean isLeader(User user) {
        return leader.userId().equals(user.userId());
    }

    public boolean isPending(String userId) {
        return pendingMembers.stream().anyMatch(u -> u.userId().equals(userId));
    }

    public boolean isRejected(String userId) {
        return rejectedMembers.contains(userId);
    }

    public Group withNewLeader(User newLeader, long newEpoch) {
        Set<User> newMembers = new HashSet<>(members);
        newMembers.remove(newLeader);
        return new Group(groupId, name, newLeader, newMembers, pendingMembers, rejectedMembers, newEpoch);
    }

    public Group withAddedMember(User newMember) {
        if (isMember(newMember) || isLeader(newMember)) {
            return this;
        }
        Set<User> newMembers = new HashSet<>(members);
        Set<User> newPending = new HashSet<>(pendingMembers);

        newMembers.add(newMember);
        newPending.remove(newMember);

        return new Group(groupId, name, leader, newMembers, newPending, rejectedMembers, epoch);
    }

    public Group withRejectedMember(User rejectedMember) {
        if (isMember(rejectedMember) || isLeader(rejectedMember)) {
            return this;
        }
        Set<String> newRejected = new HashSet<>(rejectedMembers);
        Set<User> newPending = new HashSet<>(pendingMembers);

        newRejected.add(rejectedMember.userId());
        newPending.remove(rejectedMember);

        return new Group(groupId, name, leader, members, newPending, newRejected, epoch);
    }

    public Group withRemovedMember(User memberToRemove) {
        //TODO : This is meant for when a user willingly leaves the group after joining.
        // We should start an election automatically if he is the leader (not to be done here), and simply remove him from the active members, but not add him to the rejected members.
        return this;
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
