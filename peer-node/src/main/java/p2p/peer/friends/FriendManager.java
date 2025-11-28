package p2p.peer.friends;

import p2p.common.model.User;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages friend relationships and friend requests.
 */
public class FriendManager {

    private final User localUser;
    private final VectorClock vectorClock;

    // We store the user id, not the username.
    private final Map<String, User> friends = new ConcurrentHashMap<>();

    /**
     * This is fundamentally flawded as it assumes that the username is unique which
     * isn't the case.
     * For now, we'll keep it for simplicity, as the way we handle commands in the
     * UI relies on the username.
     */
    private final Map<String, String> friendUserNameToId = new ConcurrentHashMap<>();
    private final Map<String, User> pendingRequests = new ConcurrentHashMap<>();

    public FriendManager(User localUser, VectorClock vectorClock) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
    }

    /**
     * Send a friend request to another user.
     */
    public void sendFriendRequest(User target) throws Exception {
        if (friends.containsKey(target.getUserId())) {
            System.out.println("[Friends] Already friends with " + target.getUsername());
            return;
        }

        // Increment vector clock
        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }

        // Connect to target peer via RMI
        Registry registry = LocateRegistry.getRegistry(target.getIpAddress(), target.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");

        // Send friend request
        peerService.receiveFriendRequest(localUser, vectorClock.clone());

        System.out.println("[Friends] Friend request sent to " + target.getUsername());
    }

    /**
     * Accept a friend request.
     */
    public void acceptFriendRequest(String username) throws Exception {
        User requester = pendingRequests.get(username);
        if (requester == null) {
            throw new IllegalArgumentException("No pending request from " + username);
        }
        acceptFriendRequest(requester);
    }

    public void rejectFriendRequest(String username) throws Exception {
        pendingRequests.remove(username);
        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }
    }

    private void acceptFriendRequest(User requester) throws Exception {
        // Remove from pending and add to friends
        pendingRequests.remove(requester.getUserId());
        friends.put(requester.getUserId(), requester);

        // Increment vector clock
        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }

        // Notify requester via RMI
        Registry registry = LocateRegistry.getRegistry(requester.getIpAddress(), requester.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");
        peerService.acceptFriendRequest(localUser, vectorClock.clone());

        System.out.println("[Friends] Accepted friend request from " + requester.getUsername());
    }

    /**
     * Handle incoming friend request (called by RMI).
     */
    public void handleFriendRequest(User requester) {
        if (friends.containsKey(requester.getUserId())) {
            return; // Already friends
        }
        pendingRequests.put(requester.getUserId(), requester);
    }

    /**
     * Handle friend acceptance (called by RMI).
     */
    public void handleFriendAcceptance(User accepter) {
        friends.put(accepter.getUserId(), accepter);
    }

    /**
     * Check if a user is a friend.
     */
    public boolean isFriend(String userId) {
        return friends.containsKey(userId);
    }

    /**
     * Get a friend by username.
     */
    public User getFriendByUsername(String username) {
        return friends.get(friendUserNameToId.get(username));
    }

    /**
     * Get all friends.
     */
    public List<User> getFriends() {
        return new ArrayList<>(friends.values());
    }

    /**
     * Get all pending requests.
     */
    public List<User> getPendingRequests() {
        return new ArrayList<>(pendingRequests.values());
    }
}
