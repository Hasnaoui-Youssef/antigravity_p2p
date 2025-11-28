package p2p.peer.friends;

import p2p.common.model.User;
import p2p.common.model.message.FriendMessage;
import p2p.common.rmi.PeerService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.PeerEventListener;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages friend relationships and friend requests.
 */
public class FriendManager {

    private final User localUser;
    private final VectorClock vectorClock;

    // We store the user id, not the username.
    private final Map<String, User> friends = new ConcurrentHashMap<>();

    // Map username -> userId for quick lookups
    private final Map<String, String> friendUserNameToId = new ConcurrentHashMap<>();

    private final Map<String, User> pendingRequests = new ConcurrentHashMap<>();

    private final List<PeerEventListener> listeners = new CopyOnWriteArrayList<>();

    public FriendManager(User localUser, VectorClock vectorClock) {
        this.localUser = localUser;
        this.vectorClock = vectorClock;
    }

    public void addEventListener(PeerEventListener listener) {
        listeners.add(listener);
    }

    public void removeEventListener(PeerEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Send a friend request to another user.
     */
    public void sendFriendRequest(User target) throws Exception {
        if (friends.containsKey(target.getUserId())) {
            notifyLog("Already friends with " + target.getUsername());
            return;
        }

        // Increment vector clock
        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }

        // Connect to target peer via RMI
        Registry registry = LocateRegistry.getRegistry(target.getIpAddress(), target.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");

        // Send friend request message
        FriendMessage message = FriendMessage.create(localUser, FriendMessage.SubTopic.FRIEND_REQUEST);
        peerService.receiveMessage(message);

        notifyLog("Friend request sent to " + target.getUsername());
    }

    /**
     * Accept a friend request.
     */
    public void acceptFriendRequest(String username) throws Exception {
        // Find request by username
        User requester = pendingRequests.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);

        if (requester == null) {
            throw new IllegalArgumentException("No pending request from " + username);
        }
        acceptFriendRequest(requester);
    }

    public void rejectFriendRequest(String username) throws Exception {
        // Find request by username
        User requester = pendingRequests.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);

        if (requester != null) {
            pendingRequests.remove(requester.getUserId());
            synchronized (vectorClock) {
                vectorClock.increment(localUser.getUserId());
            }

            // Notify requester via RMI
            try {
                Registry registry = LocateRegistry.getRegistry(requester.getIpAddress(), requester.getRmiPort());
                PeerService peerService = (PeerService) registry.lookup("PeerService");
                FriendMessage message = FriendMessage.create(localUser, FriendMessage.SubTopic.FRIEND_REJECT);
                peerService.receiveMessage(message);
            } catch (Exception e) {
                notifyLog("Failed to send reject message to " + username);
            }

            notifyLog("Rejected friend request from " + username);
        }
    }

    private void acceptFriendRequest(User requester) throws Exception {
        // Remove from pending and add to friends
        pendingRequests.remove(requester.getUserId());
        addFriend(requester);

        // Increment vector clock
        synchronized (vectorClock) {
            vectorClock.increment(localUser.getUserId());
        }

        // Notify requester via RMI
        Registry registry = LocateRegistry.getRegistry(requester.getIpAddress(), requester.getRmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");

        FriendMessage message = FriendMessage.create(localUser, FriendMessage.SubTopic.FRIEND_ACCEPT);
        peerService.receiveMessage(message);

        notifyLog("Accepted friend request from " + requester.getUsername());
    }

    /**
     * Handle incoming friend request (called by PeerController).
     */
    public void handleFriendRequest(User requester) {
        if (friends.containsKey(requester.getUserId())) {
            return; // Already friends
        }
        pendingRequests.put(requester.getUserId(), requester);

        // Notify listeners
        for (PeerEventListener listener : listeners) {
            listener.onFriendRequest(requester);
        }
    }

    /**
     * Handle friend acceptance (called by PeerController).
     */
    public void handleFriendAcceptance(User accepter) {
        addFriend(accepter);

        // Notify listeners
        for (PeerEventListener listener : listeners) {
            listener.onFriendRequestAccepted(accepter);
        }
    }

    /**
     * Handle friend rejection (called by PeerController).
     */
    public void handleFriendRejection(User rejecter) {
        notifyLog(rejecter.getUsername() + " rejected your friend request.");
    }

    private void addFriend(User friend) {
        friends.put(friend.getUserId(), friend);
        friendUserNameToId.put(friend.getUsername(), friend.getUserId());
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
        String userId = friendUserNameToId.get(username);
        if (userId == null)
            return null;
        return friends.get(userId);
    }

    /**
     * Get a friend by user ID.
     */
    public User getFriendById(String userId) {
        return friends.get(userId);
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

    private void notifyLog(String message) {
        for (PeerEventListener listener : listeners) {
            listener.onLog(message);
        }
    }
}
