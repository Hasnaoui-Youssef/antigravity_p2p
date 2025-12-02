package p2p.common.model;

import java.util.*;

/**
 * Represents a group in pending state awaiting invitation responses.
 */
public class PendingGroup {
    private final String groupId;
    private final String groupName;
    private final User creator;
    private final Map<String, User> potentialMembers; // userId -> User (all invited, excluding creator)
    private final Set<String> acceptedMemberIds; // Those who accepted
    private final Set<String> rejectedMemberIds; // Those who rejected

    public PendingGroup(String groupId, String groupName, User creator, List<User> potentialMembers) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.creator = creator;
        this.potentialMembers = new HashMap<>();
        for (User user : potentialMembers) {
            this.potentialMembers.put(user.userId(), user);
        }
        this.acceptedMemberIds = new HashSet<>();
        this.rejectedMemberIds = new HashSet<>();
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public User getCreator() {
        return creator;
    }

    public Map<String, User> getPotentialMembers() {
        return new HashMap<>(potentialMembers);
    }

    public Set<String> getPotentialMemberIds() {
        return new HashSet<>(potentialMembers.keySet());
    }

    public Set<String> getAcceptedMemberIds() {
        return new HashSet<>(acceptedMemberIds);
    }

    public Set<String> getRejectedMemberIds() {
        return new HashSet<>(rejectedMemberIds);
    }

    /**
     * Record an acceptance from a member.
     */
    public void recordAcceptance(String userId) {
        rejectedMemberIds.remove(userId);
        acceptedMemberIds.add(userId);
    }

    /**
     * Record a rejection from a member.
     */
    public void recordRejection(String userId) {
        acceptedMemberIds.remove(userId);
        rejectedMemberIds.add(userId);
    }

    /**
     * Check if all invited members have responded.
     */
    public boolean allResponded() {
        return acceptedMemberIds.size() + rejectedMemberIds.size() == potentialMembers.size();
    }

    /**
     * Check if the group can be finalized (min 3 members total including creator).
     */
    public boolean canFinalize() {
        return acceptedMemberIds.size() > 3;
    }

    /**
     * Check if it's impossible to reach the minimum (all invited, but < 2
     * accepted).
     */
    public boolean isImpossible() {
        // If all have responded and we don't have 2+ acceptances, impossible
        return allResponded() && !canFinalize();
    }

    /**
     * Get non-responders (for timeout handling).
     */
    public Set<String> getNonResponders() {
        Set<String> nonResponders = new HashSet<>(potentialMembers.keySet());
        nonResponders.removeAll(acceptedMemberIds);
        nonResponders.removeAll(rejectedMemberIds);
        return nonResponders;
    }
}
