package p2p.peer.groups;

import p2p.common.model.Group;
import p2p.common.model.GroupEvent;
import p2p.common.model.User;
import p2p.common.model.message.*;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.PeerEventListener;
import p2p.peer.friends.FriendManager;
import p2p.common.rmi.PeerService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages active groups, membership, and leader election.
 * Group invitations work similarly to friend requests - they are stored
 * in a pending map and the user can accept or reject them via public methods.
 */
public class GroupManager {

    private final User localUser;
    private final VectorClock vectorClock;
    private final FriendManager friendManager;

    // groupId -> Group
    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    // groupId -> Set<Message>
    private final Map<String, Set<ChatMessage>> groupMessages = new ConcurrentHashMap<>();

    // groupId -> PendingGroup (for groups awaiting invitation responses - as
    // creator)
    private final Map<String, PendingGroup> pendingGroups = new ConcurrentHashMap<>();

    // groupId -> GroupInvitationMessage (for pending invitations we received - as
    // invitee)
    private final Map<String, GroupInvitationMessage> pendingInvitations = new ConcurrentHashMap<>();

    private LeaderElectionManager electionManager;

    private final List<PeerEventListener> listeners = new CopyOnWriteArrayList<>();

    public GroupManager(User localUser, VectorClock vectorClock, FriendManager friendManager) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
        this.friendManager = friendManager;
    }

    public void addEventListener(PeerEventListener listener) {
        listeners.add(listener);
    }

    public void removeEventListener(PeerEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Set the election manager (called after construction to avoid circular
     * dependency).
     */
    public void setElectionManager(LeaderElectionManager electionManager) {
        this.electionManager = electionManager;
    }

    /**
     * Creates a new group with pending invitation flow.
     * Requires at least 2 potential members (total 3 including creator).
     */
    public Group createGroup(String name, List<User> potentialMembers) {
        // Validate minimum size
        if (potentialMembers.size() < 2) {
            throw new IllegalArgumentException("Group requires at least 2 other members (3 total including creator)");
        }

        // Create temporary group and pending state
        Group tempGroup = Group.create(name, localUser, potentialMembers);
        String groupId = tempGroup.getGroupId();

        PendingGroup pending = new PendingGroup(groupId, name, localUser, potentialMembers);
        pendingGroups.put(groupId, pending);

        // Send invitations to all potential members
        GroupInvitationMessage invitation = GroupInvitationMessage.createRequest(
                localUser.getUserId(),
                groupId,
                name,
                new ArrayList<>(potentialMembers),
                vectorClock.clone());

        for (User member : potentialMembers) {
            try {
                sendInvitationToUser(member, invitation);
            } catch (Exception e) {
                notifyError("Failed to send invitation to " + member.getUsername(), e);
            }
        }

        notifyLog("Created pending group '" + name + "' with " + potentialMembers.size() + " invited");

        // Return temp group for reference (won't be added to active groups until
        // finalized)
        return tempGroup;
    }

    /**
     * Send an invitation to a specific user via RMI.
     */
    private void sendInvitationToUser(User recipient, GroupInvitationMessage invitation) throws Exception {
        Registry registry = LocateRegistry.getRegistry(recipient.getIpAddress(), recipient.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.receiveMessage(invitation);
    }

    /**
     * Handle incoming invitation request - stores in pending invitations.
     * User must call acceptGroupInvitation or rejectGroupInvitation to respond.
     */
    public void handleInvitationRequest(GroupInvitationMessage request) {
        String groupId = request.getGroupId();
        String groupName = request.getGroupName();

        // Store the invitation for later acceptance/rejection
        pendingInvitations.put(groupId, request);

        notifyLog("Received invitation to group '" + groupName + "' from " + request.getSenderId());

        // Notify listeners
        for (PeerEventListener listener : listeners) {
            listener.onGroupInvitation(request);
        }
    }

    /**
     * Accept a group invitation.
     */
    public void acceptGroupInvitation(String groupId) throws Exception {
        GroupInvitationMessage request = pendingInvitations.remove(groupId);
        if (request == null) {
            throw new IllegalArgumentException("No pending invitation for group: " + groupId);
        }

        User creator = friendManager.getFriendById(request.getSenderId());
        if (creator == null) {
            throw new IllegalStateException("Cannot accept invitation - creator not in friends list");
        }

        // Send acceptance response
        GroupInvitationMessage response = GroupInvitationMessage.createAccept(localUser.getUserId(), groupId,
                vectorClock.clone());
        sendInvitationResponse(creator, response);

        notifyLog("Accepted invitation to group " + groupId);
    }

    /**
     * Reject a group invitation.
     */
    public void rejectGroupInvitation(String groupId) throws Exception {
        GroupInvitationMessage request = pendingInvitations.remove(groupId);
        if (request == null) {
            throw new IllegalArgumentException("No pending invitation for group: " + groupId);
        }

        User creator = friendManager.getFriendById(request.getSenderId());
        if (creator != null) {
            // Send rejection response
            GroupInvitationMessage response = GroupInvitationMessage.createReject(localUser.getUserId(), groupId,
                    vectorClock.clone());
            sendInvitationResponse(creator, response);
        }

        notifyLog("Rejected invitation to group " + groupId);
    }

    /**
     * Get all pending group invitations.
     */
    public List<GroupInvitationMessage> getPendingInvitations() {
        return new ArrayList<>(pendingInvitations.values());
    }

    /**
     * Send invitation response to the group creator.
     */
    private void sendInvitationResponse(User creator, GroupInvitationMessage response) throws Exception {
        Registry registry = LocateRegistry.getRegistry(creator.getIpAddress(), creator.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.receiveMessage(response);
    }

    /**
     * Handle invitation response from an invitee.
     */
    public void handleInvitationResponse(GroupInvitationMessage response) {
        String groupId = response.getGroupId();
        String responderId = response.getSenderId();

        PendingGroup pending = pendingGroups.get(groupId);
        if (pending == null) {
            // Check if group is already active (late acceptance)
            Group activeGroup = groups.get(groupId);
            if (activeGroup != null) {
                handleLateResponse(activeGroup, response);
            } else {
                notifyLog("Late/invalid response for group " + groupId + " from " + responderId);
            }
            return;
        }

        // Record the response
        if (response.isAccepted()) {
            pending.recordAcceptance(responderId);
            notifyLog("User " + responderId + " accepted invitation to group " + groupId);
        } else {
            pending.recordRejection(responderId);
            notifyLog("User " + responderId + " rejected invitation to group " + groupId);
        }

        // Check if we can finalize (Leader + 2 accepted members = 3 total)
        if (pending.canFinalize()) {
            finalizeGroup(groupId, pending);
        } else if (pending.isImpossible()) {
            // Too many rejections, can never reach minimum size
            dissolveGroup(groupId, pending, "Cannot reach minimum group size due to rejections");
        }
    }

    /**
     * Handle a response that arrived after the group was already finalized.
     */
    private void handleLateResponse(Group group, GroupInvitationMessage response) {
        if (response.isAccepted()) {
            User newMember = friendManager.getFriends().stream()
                    .filter(u -> u.getUserId().equals(response.getSenderId()))
                    .findFirst()
                    .orElse(null);

            if (newMember != null) {
                Set<User> updatedMembers = new HashSet<>(group.getMembers());
                updatedMembers.add(newMember);
                Group updatedGroup = new Group(
                        group.getGroupId(),
                        group.getName(),
                        group.getLeaderId(),
                        updatedMembers,
                        group.getEpoch());
                updateGroup(updatedGroup);

                // Broadcast the group update to everyone (including the new member)
                List<User> allMembers = new ArrayList<>(updatedMembers);
                broadcastGroupFinalization(updatedGroup, allMembers);

                notifyLog("Late joiner " + newMember.getUsername() + " added to group " + group.getName());
            }
        }
    }

    /**
     * Finalize the group - add to active groups.
     */
    private void finalizeGroup(String groupId, PendingGroup pending) {
        // Build set of accepted User objects (excluding creator - they're the leader)
        Set<User> finalMembers = new HashSet<>();

        // Add accepted members using the stored User objects
        Map<String, User> potentialMembers = pending.getPotentialMembers();
        for (String acceptedId : pending.getAcceptedMemberIds()) {
            User acceptedUser = potentialMembers.get(acceptedId);
            if (acceptedUser != null) {
                finalMembers.add(acceptedUser);
            }
        }

        // Create the finalized group with creator and accepted members
        Group finalizedGroup = new Group(
                groupId,
                pending.getGroupName(),
                pending.getCreator().getUserId(),
                finalMembers,
                0);

        // Add to active groups
        groups.put(groupId, finalizedGroup);
        groupMessages.put(groupId, Collections.synchronizedSet(new LinkedHashSet<>()));

        // Clean up pending state
        pendingGroups.remove(groupId);

        // Record initial leader activity for this group
        if (electionManager != null) {
            electionManager.recordLeaderActivity(groupId);
        }

        notifyLog("Group '" + pending.getGroupName() + "' finalized with " +
                (finalMembers.size() + 1) + " total members");

        notifyGroupEvent(groupId, GroupEvent.CREATED, "Group finalized");

        // Broadcast group creation notification to all accepted members
        broadcastGroupFinalization(finalizedGroup, new ArrayList<>(finalMembers));
    }

    /**
     * Broadcast group finalization to accepted members - adds group to their
     * GroupManager.
     */
    private void broadcastGroupFinalization(Group group, List<User> members) {
        for (User member : members) {
            if (member.getUserId().equals(localUser.getUserId())) {
                continue; // Skip self
            }

            try {
                Registry registry = LocateRegistry.getRegistry(member.getIpAddress(), member.getRmiPort());
                PeerService peerService = (PeerService) registry.lookup("PeerService");

                // Send the finalized group to the member
                peerService.addFinalizedGroup(group);

                notifyLog("Sent finalized group to " + member.getUsername());
            } catch (Exception e) {
                notifyError("Failed to send group to " + member.getUsername(), e);
            }
        }
    }

    /**
     * Add a finalized group that this peer was invited to and accepted.
     * Called when the creator finalizes the group.
     */
    public void addFinalizedGroup(Group group) {
        if (groups.containsKey(group.getGroupId())) {
            // Update existing group (e.g. new member joined)
            groups.put(group.getGroupId(), group);
            notifyLog("Updated group '" + group.getName() + "' with " + (group.getMembers().size() + 1) + " members");
            notifyGroupEvent(group.getGroupId(), GroupEvent.UPDATED, "Group updated");
            return;
        }

        groups.put(group.getGroupId(), group);
        groupMessages.put(group.getGroupId(), Collections.synchronizedSet(new LinkedHashSet<>()));

        // Record initial leader activity
        if (electionManager != null) {
            electionManager.recordLeaderActivity(group.getGroupId());
        }

        notifyLog("Added finalized group '" + group.getName() + "' with " +
                (group.getMembers().size() + 1) + " total members");
        notifyGroupEvent(group.getGroupId(), GroupEvent.CREATED, "Joined group");
    }

    /**
     * Dissolve a pending group that cannot be finalized.
     */
    private void dissolveGroup(String groupId, PendingGroup pending, String reason) {
        notifyLog("Dissolving group " + groupId + ": " + reason);
        pendingGroups.remove(groupId);
    }

    /**
     * Dissolve an active group (e.g., when it falls below minimum size).
     */
    public void dissolveGroup(String groupId) {
        Group group = groups.remove(groupId);
        if (group != null) {
            groupMessages.remove(groupId);
            notifyLog("Group '" + group.getName() + "' dissolved (fell below minimum size)");
            notifyGroupEvent(groupId, GroupEvent.DISSOLVED, "Group dissolved");
        }
    }

    /**
     * Get a specific group by ID.
     */
    public Group getGroup(String groupId) {
        return groups.get(groupId);
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
     * Note: Listener notification is handled by PeerController.handleChatMessage()
     */
    public void addMessage(String groupId, ChatMessage message) {
        groupMessages.computeIfAbsent(groupId,
                k -> new ConcurrentSkipListSet<>(new CausalOrderComparator()));
        groupMessages.get(groupId).add(message);
    }

    /**
     * Adds multiple messages to the group history, filtering out duplicates.
     */
    public void addMessages(String groupId, List<ChatMessage> newMessages) {
        Set<ChatMessage> messages = groupMessages.computeIfAbsent(groupId,
                k -> new ConcurrentSkipListSet<>(new CausalOrderComparator()));

        for (ChatMessage msg : newMessages) {
            if (messages.add(msg)) {
                // Notify listeners about each new message
                synchronized (listeners) {
                    for (PeerEventListener listener : listeners) {
                        listener.onMessageReceived(msg);
                    }
                }
            }
        }
    }

    /**
     * Gets messages for a group.
     */
    public List<ChatMessage> getMessages(String groupId) {
        return new ArrayList<>(groupMessages.getOrDefault(groupId, Collections.emptySet()));
    }

    /**
     * Gets the latest vector clock state for a group.
     * Merges vector clocks of all messages in the group.
     */
    public VectorClock getLatestClock(String groupId) {
        Set<ChatMessage> messages = groupMessages.getOrDefault(groupId, Collections.emptySet());
        VectorClock merged = new VectorClock();

        for (ChatMessage msg : messages) {
            VectorClock msgClock = msg.getVectorClock();
            if (msgClock != null) {
                merged.update(msgClock);
            }
        }
        return merged;
    }

    private void notifyLog(String message) {
        for (PeerEventListener listener : listeners) {
            listener.onLog(message);
        }
    }

    private void notifyError(String message, Throwable t) {
        for (PeerEventListener listener : listeners) {
            listener.onError(message, t);
        }
    }

    private void notifyGroupEvent(String groupId, GroupEvent type, String message) {
        for (PeerEventListener listener : listeners) {
            listener.onGroupEvent(groupId, type, message);
        }
    }
}
