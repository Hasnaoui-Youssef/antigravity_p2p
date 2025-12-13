package p2p.peer.friends;

import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FriendManager.
 */
class FriendManagerTest {

    private User remoteUser;
    private FriendManager friendManager;

    @BeforeEach
    void setUp() {
        User localUser = User.create("Alice", "192.168.1.1", 5001);
        remoteUser = User.create("Bob", "192.168.1.2", 5002);
        VectorClock vectorClock = new VectorClock();
        friendManager = new FriendManager(localUser, vectorClock);
    }

    @Test
    @DisplayName("HandleFriendRequest should add to pending requests")
    void testHandleFriendRequest() {
        friendManager.handleFriendRequest(remoteUser);

        List<User> pending = friendManager.getPendingRequests();
        assertEquals(1, pending.size());
        assertTrue(pending.contains(remoteUser));
    }

    @Test
    @DisplayName("HandleFriendRequest should not duplicate pending requests")
    void testHandleFriendRequestDuplicate() {
        friendManager.handleFriendRequest(remoteUser);
        friendManager.handleFriendRequest(remoteUser);

        List<User> pending = friendManager.getPendingRequests();
        assertEquals(1, pending.size());
    }

    @Test
    @DisplayName("HandleFriendRequest should ignore if already friends")
    void testHandleFriendRequestAlreadyFriends() {
        friendManager.handleFriendAcceptance(remoteUser);
        friendManager.handleFriendRequest(remoteUser);

        List<User> pending = friendManager.getPendingRequests();
        assertTrue(pending.isEmpty());
    }

    @Test
    @DisplayName("HandleFriendAcceptance should add to friends")
    void testHandleFriendAcceptance() {
        friendManager.handleFriendAcceptance(remoteUser);

        assertTrue(friendManager.isFriend(remoteUser.userId()));
        List<User> friends = friendManager.getFriends();
        assertEquals(1, friends.size());
        assertTrue(friends.contains(remoteUser));
    }

    @Test
    @DisplayName("IsFriend should check friend status")
    void testIsFriend() {
        assertFalse(friendManager.isFriend(remoteUser.userId()));

        friendManager.handleFriendAcceptance(remoteUser);
        assertTrue(friendManager.isFriend(remoteUser.userId()));
    }

    @Test
    @DisplayName("GetFriendByUsername should find friend")
    void testGetFriendByUsername() {
        friendManager.handleFriendAcceptance(remoteUser);

        User found = friendManager.getFriendByUsername("Bob");
        assertNotNull(found);
        assertEquals(remoteUser, found);
    }

    @Test
    @DisplayName("GetFriendByUsername should be case-insensitive")
    void testGetFriendByUsernameCaseInsensitive() {
        friendManager.handleFriendAcceptance(remoteUser);

        List<User> friends = friendManager.getFriends();
        for (User friend : friends) {
            System.out.println(friend);
        }

        assertNotNull(friendManager.getFriendByUsername("bob"), "Non null assert at \"bob\"");
        assertNotNull(friendManager.getFriendByUsername("BOB"), "Non null assert at \"BOB\"");
    }

    @Test
    @DisplayName("GetFriendByUsername should return null if not found")
    void testGetFriendByUsernameNotFound() {
        User found = friendManager.getFriendByUsername("Charlie");
        assertNull(found);
    }

    @Test
    @DisplayName("GetFriends should return empty list initially")
    void testGetFriendsEmpty() {
        List<User> friends = friendManager.getFriends();
        assertNotNull(friends);
        assertTrue(friends.isEmpty());
    }

    @Test
    @DisplayName("GetPendingRequests should return empty list initially")
    void testGetPendingRequestsEmpty() {
        List<User> pending = friendManager.getPendingRequests();
        assertNotNull(pending);
        assertTrue(pending.isEmpty());
    }
}
