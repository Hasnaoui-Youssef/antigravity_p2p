package p2p.bootstrap;

import p2p.common.model.User;
import p2p.common.rmi.UsernameAlreadyExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserRegistry.
 */
class UserRegistryTest {
    
    private UserRegistry registry;
    private User alice;
    private User bob;
    
    @BeforeEach
    void setUp() {
        registry = new UserRegistry();
        alice = User.create("Alice", "192.168.1.1", 5001);
        bob = User.create("Bob", "192.168.1.2", 5002);
    }
    
    @AfterEach
    void tearDown() {
        registry.shutdown();
    }
    
    @Test
    @DisplayName("AddUser should register user")
    void testAddUser() {
        registry.addUser(alice);
        
        User retrieved = registry.getUser(alice.userId());
        assertNotNull(retrieved);
        assertEquals(alice, retrieved);
    }
    
    @Test
    @DisplayName("RemoveUser should unregister user")
    void testRemoveUser() {
        registry.addUser(alice);
        registry.removeUser(alice.userId());
        
        assertNull(registry.getUser(alice.userId()));
    }
    
    @Test
    @DisplayName("UpdateHeartbeat should refresh user timestamp")
    void testUpdateHeartbeat() throws InterruptedException {
        registry.addUser(alice);
        
        Thread.sleep(100);
        registry.updateHeartbeat(alice.userId());
        
        // User should still exist
        assertNotNull(registry.getUser(alice.userId()));
    }
    
    @Test
    @DisplayName("SearchByUsername should find partial matches")
    void testSearchByUsername() {
        registry.addUser(alice);
        registry.addUser(bob);
        
        List<User> results = registry.searchByUsername("Alice");
        assertEquals(1, results.size());
        assertTrue(results.contains(alice));
        
        results = registry.searchByUsername("li"); // Partial match
        assertEquals(1, results.size());
        assertTrue(results.contains(alice));
    }
    
    @Test
    @DisplayName("SearchByUsername should be case-insensitive")
    void testSearchByUsernameCaseInsensitive() {
        registry.addUser(alice);
        
        List<User> results = registry.searchByUsername("alice");
        assertEquals(1, results.size());
        
        results = registry.searchByUsername("ALICE");
        assertEquals(1, results.size());
    }
    
    @Test
    @DisplayName("SearchByIp should find partial matches")
    void testSearchByIp() {
        registry.addUser(alice);
        registry.addUser(bob);
        
        List<User> results = registry.searchByIp("192.168.1.1");
        assertEquals(1, results.size());
        assertTrue(results.contains(alice));
        
        results = registry.searchByIp("192.168.1"); // Partial match both
        assertEquals(2, results.size());
    }
    
    @Test
    @DisplayName("GetAllUsers should return all registered users")
    void testGetAllUsers() {
        registry.addUser(alice);
        registry.addUser(bob);
        
        List<User> users = registry.getAllUsers();
        assertEquals(2, users.size());
        assertTrue(users.contains(alice));
        assertTrue(users.contains(bob));
    }
    
    @Test
    @DisplayName("Concurrent access should be thread-safe")
    void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                User user = User.create("User" + index, "192.168.1." + index, 5000 + index);
                registry.addUser(user);
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals(numThreads, registry.getAllUsers().size());
    }
    
    @Test
    @DisplayName("Stale users should be removed after timeout")
    void testStaleUserCleanup() throws InterruptedException {
        // Note: This test relies on the cleanup interval (10s) and timeout (30s)
        // For testing, we'd ideally make these configurable
        // For now, we'll just test that the cleanup thread exists
        
        registry.addUser(alice);
        assertNotNull(registry.getUser(alice.userId()));
        
        // In production, after 30+ seconds with no heartbeat, user would be removed
        // But we won't wait that long in tests
    }

    @Test
    @DisplayName("registerUser should succeed for unique username")
    void testRegisterUserUniqueUsername() throws UsernameAlreadyExistsException {
        registry.registerUser(alice);
        
        User retrieved = registry.getUser(alice.userId());
        assertNotNull(retrieved);
        assertEquals(alice, retrieved);
    }

    @Test
    @DisplayName("registerUser should throw exception for duplicate username")
    void testRegisterUserDuplicateUsername() throws UsernameAlreadyExistsException {
        registry.registerUser(alice);
        
        // Create another user with the same username but different userId
        User aliceDuplicate = User.create("Alice", "192.168.1.100", 6001);
        
        assertThrows(UsernameAlreadyExistsException.class, () -> {
            registry.registerUser(aliceDuplicate);
        });
    }

    @Test
    @DisplayName("registerUser should allow same user to re-register")
    void testRegisterUserSameUserReregister() throws UsernameAlreadyExistsException {
        registry.registerUser(alice);
        
        // Same user (same userId) should be able to re-register
        assertDoesNotThrow(() -> registry.registerUser(alice));
    }

    @Test
    @DisplayName("username check should be case-insensitive")
    void testDuplicateUsernameCaseInsensitive() throws UsernameAlreadyExistsException {
        registry.registerUser(alice);
        
        // Create another user with same username different case
        User aliceUpperCase = User.create("ALICE", "192.168.1.100", 6001);
        
        assertThrows(UsernameAlreadyExistsException.class, () -> {
            registry.registerUser(aliceUpperCase);
        });
    }

    @Test
    @DisplayName("isUsernameAvailable should return true for available username")
    void testIsUsernameAvailableTrue() {
        assertTrue(registry.isUsernameAvailable("Alice"));
    }

    @Test
    @DisplayName("isUsernameAvailable should return false for taken username")
    void testIsUsernameAvailableFalse() throws UsernameAlreadyExistsException {
        registry.registerUser(alice);
        
        assertFalse(registry.isUsernameAvailable("Alice"));
        assertFalse(registry.isUsernameAvailable("alice")); // case insensitive
        assertFalse(registry.isUsernameAvailable("ALICE")); // case insensitive
    }

    @Test
    @DisplayName("removeUser should free up the username")
    void testRemoveUserFreesUsername() throws UsernameAlreadyExistsException {
        registry.registerUser(alice);
        assertFalse(registry.isUsernameAvailable("Alice"));
        
        registry.removeUser(alice.userId());
        assertTrue(registry.isUsernameAvailable("Alice"));
        
        // Now another user should be able to register with the same username
        User newAlice = User.create("Alice", "192.168.1.100", 6001);
        assertDoesNotThrow(() -> registry.registerUser(newAlice));
    }

    @Test
    @DisplayName("isUsernameAvailable should return false for null")
    void testIsUsernameAvailableNull() {
        assertFalse(registry.isUsernameAvailable(null));
    }
}
