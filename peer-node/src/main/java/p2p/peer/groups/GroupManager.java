package p2p.peer.groups;

import p2p.common.model.Group;
import p2p.common.model.Message;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active groups, membership, and leader election.
 */
public class GroupManager {
    
    private final User localUser;
    private final VectorClock vectorClock;
    
    // groupId -> Group
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    
    // groupId -> List<Message>
    private final Map<String, List<Message>> groupMessages = new ConcurrentHashMap<>();
    
    private LeaderElectionManager electionManager;
    
    public GroupManager(User localUser, VectorClock vectorClock) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
    }
    
    /**
     * Set the election manager (called after construction to avoid circular dependency).
     */
    public void setElectionManager(LeaderElectionManager electionManager) {
        this.electionManager = electionManager;
    }
    
    /**
     * Creates a new group with the specified name and initial members.
     * The local user is automatically added and becomes the leader.
     */
    public Group createGroup(String name, List<User> initialMembers) {
        Group group = Group.create(name, localUser, initialMembers);
        groups.put(group.getGroupId(), group);
        groupMessages.put(group.getGroupId(), new ArrayList<>());
        
        // Start heartbeat tracking for this group
        if (electionManager != null) {
            electionManager.recordLeaderHeartbeat(group.getGroupId());
        }
        
        return group;
    }
    
    /**
     * Adds a group to the manager (e.g. when invited).
     */
    public void addGroup(Group group) {
        groups.put(group.getGroupId(), group);
        groupMessages.putIfAbsent(group.getGroupId(), new ArrayList<>());
        
        // Start heartbeat tracking
        if (electionManager != null) {
            electionManager.recordLeaderHeartbeat(group.getGroupId());
        }
    }
    
    /**
     * Gets a group by ID.
     */
    public Optional<Group> getGroup(String groupId) {
        return Optional.ofNullable(groups.get(groupId));
    }
    
    /**
     * Gets all groups the user is a member of.
     */
    public List<Group> getGroups() {
        return new ArrayList<>(groups.values());
    }
    
    /**
     * Checks if the local user is the leader of the group.
     */
    public boolean isLeader(String groupId) {
        Group group = groups.get(groupId);
        return group != null && group.getLeaderId().equals(localUser.getUserId());
    }
    
    /**
     * Updates the group state (e.g. new member added, new leader elected).
     */
    public void updateGroup(Group updatedGroup) {
        if (groups.containsKey(updatedGroup.getGroupId())) {
            groups.put(updatedGroup.getGroupId(), updatedGroup);
        }
    }

    /**
     * Adds a message to the group history.
     */
    public void addMessage(String groupId, Message message) {
        groupMessages.computeIfAbsent(groupId, k -> new ArrayList<>()).add(message);
    }

    /**
     * Gets messages for a group.
     */
    public List<Message> getMessages(String groupId) {
        return new ArrayList<>(groupMessages.getOrDefault(groupId, Collections.emptyList()));
    }
}
