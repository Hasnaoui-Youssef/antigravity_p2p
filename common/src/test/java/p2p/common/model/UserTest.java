package p2p.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User model.
 */
class UserTest {
    
    @Test
    @DisplayName("Create should generate unique user ID")
    void testCreate() {
        User user1 = User.create("Alice", "192.168.1.1", 5001);
        User user2 = User.create("Bob", "192.168.1.2", 5002);
        
        assertNotNull(user1.getUserId());
        assertNotNull(user2.getUserId());
        assertNotEquals(user1.getUserId(), user2.getUserId());
    }
    
    @Test
    @DisplayName("Constructor should validate non-null parameters")
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> 
            new User(null, "Alice", "192.168.1.1", 5001));
        
        assertThrows(NullPointerException.class, () -> 
            new User("id123", null, "192.168.1.1", 5001));
        
        assertThrows(NullPointerException.class, () -> 
            new User("id123", "Alice", null, 5001));
    }
    
    @Test
    @DisplayName("Equals should compare by user ID only")
    void testEquals() {
        User user1 = new User("id123", "Alice", "192.168.1.1", 5001);
        User user2 = new User("id123", "Bob", "192.168.1.2", 5002); // Different name/IP but same ID
        User user3 = new User("id456", "Alice", "192.168.1.1", 5001);
        
        assertEquals(user1, user2); // Same ID
        assertNotEquals(user1, user3); // Different ID
    }
    
    @Test
    @DisplayName("HashCode should be consistent with equals")
    void testHashCode() {
        User user1 = new User("id123", "Alice", "192.168.1.1", 5001);
        User user2 = new User("id123", "Bob", "192.168.1.2", 5002);
        
        assertEquals(user1.hashCode(), user2.hashCode());
    }
    
    @Test
    @DisplayName("ToString should include username and connection details")
    void testToString() {
        User user = new User("id123", "Alice", "192.168.1.1", 5001);
        String str = user.toString();
        
        assertTrue(str.contains("Alice"));
        assertTrue(str.contains("192.168.1.1"));
        assertTrue(str.contains("5001"));
    }
    
    @Test
    @DisplayName("Getters should return correct values")
    void testGetters() {
        User user = new User("id123", "Alice", "192.168.1.1", 5001);
        
        assertEquals("id123", user.getUserId());
        assertEquals("Alice", user.getUsername());
        assertEquals("192.168.1.1", user.getIpAddress());
        assertEquals(5001, user.getRmiPort());
    }
}
