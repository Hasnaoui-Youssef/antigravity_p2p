package p2p.peer.groups;

import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.common.model.message.*;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.friends.FriendManager;
import p2p.common.rmi.PeerService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages active groups, membership, and leader election.
 */
public class GroupManager {
    
    private final User localUser;
    private final VectorClock vectorClock;
    private final FriendManager friendManager;
    
    // groupId -> Group
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    
    // groupId -> List<Message>
    private final Map<String, List<Message>> groupMessages = new ConcurrentHashMap<>();
    
    // groupId -> PendingGroup (for groups awaiting invitation responses)
    private final Map<String, PendingGroup> pendingGroups = new ConcurrentHashMap<>();
    
    // Scheduler for invitation timeouts
    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(1);
    
    private static final int INVITATION_TIMEOUT_SECONDS = 3;
    private static final int MIN_GROUP_SIZE = 3; // Creator + 2 others
    
    private LeaderElectionManager electionManager;
    private InvitationHandler invitationHandler; // Callback for handling invitations
    
    public GroupManager(User localUser, VectorClock vectorClock, FriendManager friendManager) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
        this.friendManager = friendManager;
    }
    
    /**
     * Set the election manager (called after construction to avoid circular dependency).
     */
    public void setElectionManager(LeaderElectionManager electionManager) {
        this.electionManager = electionManager;
    }
    
    /**
     * Set the invitation handler (allows UI/tests to control accept/reject decisions).
     */
    public void setInvitationHandler(InvitationHandler handler) {
        this.invitationHandler = handler;
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
        GroupInvitationRequest invitation = GroupInvitationRequest.create(
            localUser.getUserId(),
            groupId,
            name,
            new ArrayList<>(potentialMembers)
        );
        
        for (User member : potentialMembers) {
            try {
                sendInvitationToUser(member, invitation);
            } catch (Exception e) {
                System.err.println("[GroupManager] Failed to send invitation to " + member.getUsername() + ": " + e.getMessage());
            }
        }
        
        // Schedule timeout (3 seconds)
        ScheduledFuture<?> timeout = timeoutScheduler.schedule(
            () -> handleInvitationTimeout(groupId),
            INVITATION_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        );
        pending.setTimeoutTask(timeout);
        
        System.out.println("[GroupManager] Created pending group '" + name + "' with " + potentialMembers.size() + " invited");
        
        // Return temp group for reference  (won't be added to active groups until finalized)
        return tempGroup;
    }
    
    /**
     * Send an invitation to a specific user via RMI.
     */
    private void sendInvitationToUser(User recipient, GroupInvitationRequest invitation) throws Exception {
        Registry registry = LocateRegistry.getRegistry(recipient.getIpAddress(), recipient.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.receiveMessage(invitation);
    }
    
    /**
     * Handle invitation timeout - treat non-responders as rejected.
     */
    private void handleInvitationTimeout(String groupId) {
        PendingGroup pending = pendingGroups.get(groupId);
        if (pending == null) {
            return; // Already finalized or dissolved
        }
        
        // Mark all non-responders as rejected
        Set<String> nonResponders = pending.getNonResponders();
        for (String userId : nonResponders) {
            pending.recordRejection(userId);
            System.out.println("[GroupManager] Invitation timeout for user " + userId + " in group " + groupId);
        }
        
        // Check if we can finalize
        if (pending.canFinalize()) {
            finalizeGroup(groupId, pending);
        } else {
            dissolveGroup(groupId, pending, "Insufficient acceptances after timeout");
        }
    }
    
    /**
     * Handle incoming invitation request using the registered InvitationHandler.
     * @param request The invitation request
     */
    public void handleInvitationRequest(GroupInvitationRequest request) {
        String groupId = request.getGroupId();
        String groupName = request.getGroupName();
        
        System.out.println("[GroupManager] Received invitation to group '" + groupName + "' from " + request.getSenderId());
        
        // Use callback to determine accept/reject (or default to reject if no handler)
        boolean accept = false;
        if (invitationHandler != null) {
            accept = invitationHandler.onInvitationReceived(request);
        } else {
            System.err.println("[GroupManager] No InvitationHandler set - rejecting invitation by default");
        }
        
        System.out.println("[GroupManager] Decision: " + (accept ? "ACCEPTING" : "REJECTING"));
        
        GroupInvitationResponse.Status status = accept ? 
            GroupInvitationResponse.Status.ACCEPTED : 
            GroupInvitationResponse.Status.REJECTED;
        
        // Send response back to creator
        GroupInvitationResponse response = GroupInvitationResponse.create(
            localUser.getUserId(),
            groupId,
            status
        );
        
        try {
            // Find the creator from our friends list (they must be a friend to invite us)
            // We can't use potentialMembers because that only contains invitees, not the creator
            User creator = null;
            for (User friend : friendManager.getFriends()) {
                if (friend.getUserId().equals(request.getSenderId())) {
                    creator = friend;
                    break;
                }
            }
            
            if (creator != null) {
                sendInvitationResponse(creator, response);
            } else {
                System.err.println("[GroupManager] Cannot send response - creator not in friends list");
            }
        } catch (Exception e) {
            System.err.println("[GroupManager] Failed to send invitation response: " + e.getMessage());
        }
    }
    
    /**
     * Send invitation response to the group creator.
     */
    private void sendInvitationResponse(User creator, GroupInvitationResponse response) throws Exception {
        Registry registry = LocateRegistry.getRegistry(creator.getIpAddress(), creator.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.receiveMessage(response);
    }
    
    /**
     * Handle invitation response from an invitee.
     */
    public void handleInvitationResponse(GroupInvitationResponse response) {
        String groupId = response.getGroupId();
        String responderId = response.getSenderId();
        
        PendingGroup pending = pendingGroups.get(groupId);
        if (pending == null) {
            System.out.println("[GroupManager] Late/invalid response for group " + groupId + " from " + responderId);
            return; // Group already finalized or doesn't exist
        }
        
        // Record the response
        if (response.getStatus() == GroupInvitationResponse.Status.ACCEPTED) {
            pending.recordAcceptance(responderId);
            System.out.println("[GroupManager] User " + responderId + " accepted invitation to group " + groupId);
        } else {
            pending.recordRejection(responderId);
            System.out.println("[GroupManager] User " + responderId + " rejected invitation to group " + groupId);
        }
        
        // Check if all responded
        if (pending.allResponded()) {
            pending.cancelTimeout(); // Cancel timeout since all responded
            
            if (pending.canFinalize()) {
                finalizeGroup(groupId, pending);
            } else {
                dissolveGroup(groupId, pending, "Insufficient acceptances");
            }
        } else if (pending.isImpossible()) {
            // All responded but can't reach quorum
            pending.cancelTimeout();
            dissolveGroup(groupId, pending, "Cannot reach minimum group size");
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
                // Sharing the same User reference - safe because User is immutable
                finalMembers.add(acceptedUser);
            }
        }
        
        // Create the finalized group with creator and accepted members
        // Group.create will filter out creator from members if somehow included
        Group finalizedGroup = new Group(
            groupId,
            pending.getGroupName(),
            pending.getCreator().getUserId(),
            finalMembers,
            0
        );
        
        // Add to active groups
        groups.put(groupId, finalizedGroup);
        groupMessages.put(groupId, new ArrayList<>());
        
        // Clean up pending state
        pendingGroups.remove(groupId);
        
        // Record initial leader activity for this group
        if (electionManager != null) {
            electionManager.recordLeaderActivity(groupId);
        }
        
        System.out.println("[GroupManager] Group '" + pending.getGroupName() + "' finalized with " + 
                         (finalMembers.size() + 1) + " total members (leader + " + finalMembers.size() + " members)");
        
        // Broadcast group creation notification to all accepted members
        broadcastGroupFinalization(finalizedGroup, new ArrayList<>(finalMembers));
    }
    
    /**
     * Broadcast group finalization to accepted members - adds group to their GroupManager.
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
                
                System.out.println("[GroupManager] Sent finalized group to " + member.getUsername());
            } catch (Exception e) {
                System.err.println("[GroupManager] Failed to send group to " + member.getUsername() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Add a finalized group that this peer was invited to and accepted.
     * Called when the creator finalizes the group.
     */
    public void addFinalizedGroup(Group group) {
        if (groups.containsKey(group.getGroupId())) {
            System.out.println("[GroupManager] Group " + group.getGroupId() + " already exists, skipping");
            return;
        }
        
        groups.put(group.getGroupId(), group);
        groupMessages.put(group.getGroupId(), new ArrayList<>());
        
        // Record initial leader activity
        if (electionManager != null) {
            electionManager.recordLeaderActivity(group.getGroupId());
        }
        
        System.out.println("[GroupManager] Added finalized group '" + group.getName() + "' with " + 
                         (group.getMembers().size() + 1) + " total members");
    }
    
    /**
     * Dissolve a pending group that cannot be finalized.
     */
    private void dissolveGroup(String groupId, PendingGroup pending, String reason) {
        System.out.println("[GroupManager] Dissolving group " + groupId + ": " + reason);
        pendingGroups.remove(groupId);
        
        // TODO: Notify invited users that group was dissolved
    }
    
    /**
     * Dissolve an active group (e.g., when it falls below minimum size).
     */
    public void dissolveGroup(String groupId) {
        Group group = groups.remove(groupId);
        if (group != null) {
            groupMessages.remove(groupId);
            System.out.println("[GroupManager] Group '" + group.getName() + 
                "' dissolved (fell below minimum size)");
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
     */
    public void addMessage(String groupId, Message message) {
        List<Message> messages = groupMessages.computeIfAbsent(groupId, k -> new ArrayList<>());
        // Check for duplicates by message ID
        boolean exists = messages.stream()
            .anyMatch(m -> m.getMessageId().equals(message.getMessageId()));
        if (!exists) {
            messages.add(message);
        }
    }
    
    /**
     * Adds multiple messages to the group history, filtering out duplicates.
     */
    public void addMessages(String groupId, List<Message> newMessages) {
        List<Message> messages = groupMessages.computeIfAbsent(groupId, k -> new ArrayList<>());
        Set<String> existingIds = messages.stream()
            .map(Message::getMessageId)
            .collect(Collectors.toSet());
        
        for (Message msg : newMessages) {
            if (!existingIds.contains(msg.getMessageId())) {
                messages.add(msg);
                existingIds.add(msg.getMessageId());
            }
        }
    }

    /**
     * Gets messages for a group.
     */
    /**
     * Gets messages for a group.
     */
    public List<Message> getMessages(String groupId) {
        return new ArrayList<>(groupMessages.getOrDefault(groupId, Collections.emptyList()));
    }

    /**
     * Gets the latest vector clock state for a group.
     * Merges vector clocks of all messages in the group.
     */
    public VectorClock getLatestClock(String groupId) {
        List<Message> messages = groupMessages.getOrDefault(groupId, Collections.emptyList());
        VectorClock merged = new VectorClock();
        
        for (Message msg : messages) {
            VectorClock msgClock = null;
            if (msg instanceof p2p.common.model.message.DirectMessage) {
                msgClock = ((p2p.common.model.message.DirectMessage) msg).getVectorClock();
            } else if (msg instanceof p2p.common.model.message.GroupMessage) {
                msgClock = ((p2p.common.model.message.GroupMessage) msg).getVectorClock();
            }
            
            if (msgClock != null) {
                merged.update(msgClock);
            }
        }
        return merged;
    }
}
