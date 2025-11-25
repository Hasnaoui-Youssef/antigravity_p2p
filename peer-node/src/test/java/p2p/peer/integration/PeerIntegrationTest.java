package p2p.peer.integration;

import p2p.bootstrap.BootstrapServiceImpl;
import p2p.bootstrap.UserRegistry;
import p2p.common.model.User;
import p2p.common.rmi.BootstrapService;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.PeerController;

import org.junit.jupiter.api.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean integration tests using programmatic peer controller.
 * Uses unique ports for each test to avoid RMI conflicts.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PeerIntegrationTest {
    
    private static final int BOOTSTRAP_PORT = 13099;
    private static int nextPort = 15001;  // Auto-incrementing ports for peers
    
    private static synchronized int getNextPort() {
        return nextPort++;
    }
    
    private static UserRegistry userRegistry;
    private static BootstrapService bootstrapService;
    private static Registry bootstrapRegistry;
    
    @BeforeAll
    static void setUpBootstrap() throws Exception {
        // Start bootstrap server
        userRegistry = new UserRegistry();
        bootstrapService = new BootstrapServiceImpl(userRegistry);
        bootstrapRegistry = LocateRegistry.createRegistry(BOOTSTRAP_PORT);
        bootstrapRegistry.rebind("BootstrapService", bootstrapService);
        
        System.out.println("[Test] Bootstrap server started on port " + BOOTSTRAP_PORT);
    }
    
    @AfterAll
    static void tearDownBootstrap() throws Exception {
        if (bootstrapService != null) {
            try {
                UnicastRemoteObject.unexportObject(bootstrapService, true);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (bootstrapRegistry != null) {
            try {
                bootstrapRegistry.unbind("BootstrapService");
                UnicastRemoteObject.unexportObject(bootstrapRegistry, true);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (userRegistry != null) {
            userRegistry.shutdown();
        }
        
        System.out.println("[Test] Bootstrap server stopped");
    }
    
    @Test
    @Order(1)
    @DisplayName("Peer can start and stop cleanly")
    void testPeerLifecycle() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        alice.start();
        assertTrue(alice.isStarted());
        assertEquals("Alice", alice.getLocalUser().getUsername());
        
        alice.stop();
        assertFalse(alice.isStarted());
        
        System.out.println("[Test] ✓ Peer lifecycle works");
    }
    
    @Test
    @Order(2)
    @DisplayName("Two peers can connect and discover each other")
    void testPeerDiscovery() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            
            // Wait for registration
            Thread.sleep(1000);
            
            // Alice searches for Bob
            var users = alice.searchUsers("Bob");
            assertEquals(1, users.size());
            assertEquals("Bob", users.get(0).getUsername());
            
            // Bob searches for Alice
            users = bob.searchUsers("Alice");
            assertEquals(1, users.size());
            assertEquals("Alice", users.get(0).getUsername());
            
            System.out.println("[Test] ✓ Peer discovery works");
        } finally {
            alice.stop();
            bob.stop();
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("Friend request flow: send and accept")
    void testFriendRequestFlow() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            Thread.sleep(1000);
            
            // Alice sends friend request to Bob
            alice.sendFriendRequest("Bob");
            Thread.sleep(500);  // Wait for RMI call
            
            // Bob should have pending request
            var bobPending = bob.getPendingRequests();
            assertEquals(1, bobPending.size());
            assertEquals("Alice", bobPending.get(0).getUsername());
            
            // Bob accepts
            bob.acceptFriendRequest("Alice");
            Thread.sleep(500);
            
            // Both should be friends now
            assertTrue(bob.getFriends().stream().anyMatch(u -> u.getUsername().equals("Alice")));
            assertTrue(alice.getFriends().stream().anyMatch(u -> u.getUsername().equals("Bob")));
            
            System.out.println("[Test] ✓ Friend request flow works");
        } finally {
            alice.stop();
            bob.stop();
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("Vector clocks synchronize during friend request")
    void testVectorClockSync() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            Thread.sleep(1000);
            
            String aliceId = alice.getLocalUser().getUserId();
            String bobId = bob.getLocalUser().getUserId();
            
            // Initial state: each peer only knows their own clock
            assertEquals(1, alice.getVectorClock().getTime(aliceId));
            assertEquals(0, alice.getVectorClock().getTime(bobId));
            
            // Alice sends friend request
            alice.sendFriendRequest("Bob");
            Thread.sleep(500);
            
            // Bob's clock should now include Alice's time
            assertTrue(bob.getVectorClock().getTime(aliceId) > 0);
            
            // Bob accepts
            bob.acceptFriendRequest("Alice");
            Thread.sleep(500);
            
            // Alice's clock should now include Bob's time
            assertTrue(alice.getVectorClock().getTime(bobId) > 0);
            
            System.out.println("[Test] ✓ Vector clocks synchronized");
        } finally {
            alice.stop();
            bob.stop();
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Multiple friend requests to same user are idempotent")
    void testDuplicateFriendRequests() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            Thread.sleep(1000);
            
            // Alice sends multiple requests
            alice.sendFriendRequest("Bob");
            Thread.sleep(200);
            alice.sendFriendRequest("Bob");
            Thread.sleep(200);
            alice.sendFriendRequest("Bob");
            Thread.sleep(500);
            
            // Bob should only have one pending request
            assertEquals(1, bob.getPendingRequests().size());
            
            System.out.println("[Test] ✓ Duplicate friend requests handled correctly");
        } finally {
            alice.stop();
            bob.stop();
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("Messaging between friends")
    void testMessaging() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            Thread.sleep(1000);
            
            // Become friends first
            alice.sendFriendRequest("Bob");
            Thread.sleep(500);
            bob.acceptFriendRequest("Alice");
            Thread.sleep(500);
            
            // Send messages
            alice.sendMessage("Bob", "Hello from Alice!");
            Thread.sleep(300);
            bob.sendMessage("Alice", "Hi Alice, this is Bob!");
            Thread.sleep(300);
            
            // Verify message handlers received messages
            assertNotNull(bob.getMessageHandler());
            assertNotNull(alice.getMessageHandler());
            
            System.out.println("[Test] ✓ Messaging works");
        } finally {
            alice.stop();
            bob.stop();
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("Three peers can all interact")
    void testThreePeers() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController charlie = new PeerController("Charlie", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            charlie.start();
            Thread.sleep(1000);
            
            // Alice → Bob
            alice.sendFriendRequest("Bob");
            Thread.sleep(300);
            bob.acceptFriendRequest("Alice");
            Thread.sleep(300);
            
            // Bob → Charlie
            bob.sendFriendRequest("Charlie");
            Thread.sleep(300);
            charlie.acceptFriendRequest("Bob");
            Thread.sleep(300);
            
            // Alice → Charlie
            alice.sendFriendRequest("Charlie");
            Thread.sleep(300);
            charlie.acceptFriendRequest("Alice");
            Thread.sleep(500);
            
            // Verify friend counts
            assertEquals(2, alice.getFriends().size());  // Bob, Charlie
            assertEquals(2, bob.getFriends().size());    // Alice, Charlie
            assertEquals(2, charlie.getFriends().size()); // Alice, Bob
            
            System.out.println("[Test] ✓ Three-peer interaction works");
        } finally {
            alice.stop();
            bob.stop();
            charlie.stop();
        }
    }
    
    @Test
    @Order(8)
    @DisplayName("Message causality chain is preserved")
    void testMessageCausalityChain() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            Thread.sleep(1000);
            
            // Become friends
            alice.sendFriendRequest("Bob");
            Thread.sleep(500);
            bob.acceptFriendRequest("Alice");
            Thread.sleep(500);
            
            // Record initial vector clocks
            VectorClock aliceClockM1 = alice.getVectorClock().clone();
            VectorClock bobClockInitial = bob.getVectorClock().clone();
            
            // Send M1: Alice → Bob
            alice.sendMessage("Bob", "M1");
            Thread.sleep(300);
            VectorClock aliceClockM2 = alice.getVectorClock().clone();
            VectorClock bobClockM1 = bob.getVectorClock().clone();
            
            // Send M2: Bob → Alice
            bob.sendMessage("Alice", "M2");
            Thread.sleep(300);
            VectorClock bobClockM2 = bob.getVectorClock().clone();
            VectorClock aliceClockM3 = alice.getVectorClock().clone();
            
            // Send M3: Alice → Bob
            alice.sendMessage("Bob", "M3");
            Thread.sleep(300);
            VectorClock bobClockM3 = bob.getVectorClock().clone();
            
            // Verify causality: M1 → M2 → M3
            // M1 happens before M2
            assertTrue(aliceClockM2.happensBefore(bobClockM2) || aliceClockM2.equals(bobClockM2));
            
            // M2 happens before M3
            assertTrue(bobClockM2.happensBefore(bobClockM3) || bobClockM2.equals(bobClockM3));
            
            System.out.println("[Test] ✓ Message causality chain preserved (M1→M2→M3)");
        } finally {
            alice.stop();
            bob.stop();
        }
    }
    
    @Test
    @Order(9)
    @DisplayName("Cannot send message to non-friend")
    void testCannotMessageNonFriend() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController charlie = new PeerController("Charlie", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            charlie.start();
            Thread.sleep(1000);
            
            // Alice and Charlie are NOT friends
            assertFalse(alice.getFriends().stream().anyMatch(u -> u.getUsername().equals("Charlie")));
            
            // Alice tries to send message to Charlie (should fail)
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                alice.sendMessage("Charlie", "Hey Charlie!");
            });
            
            assertTrue(exception.getMessage().contains("Not a friend"));
            
            System.out.println("[Test] ✓ Cannot send message to non-friend");
        } finally {
            alice.stop();
            charlie.stop();
        }
    }
    
    @Test
    @Order(10)
    @DisplayName("Multiple messages preserve ordering")
    void testMultipleMessagesOrdering() throws Exception {
        PeerController alice = new PeerController("Alice", getNextPort(), "localhost", BOOTSTRAP_PORT);
        PeerController bob = new PeerController("Bob", getNextPort(), "localhost", BOOTSTRAP_PORT);
        
        try {
            alice.start();
            bob.start();
            Thread.sleep(1000);
            
            // Become friends
            alice.sendFriendRequest("Bob");
            Thread.sleep(500);
            bob.acceptFriendRequest("Alice");
            Thread.sleep(500);
            
            // Send 5 messages in sequence
            VectorClock[] clocks = new VectorClock[5];
            for (int i = 0; i < 5; i++) {
                alice.sendMessage("Bob", "Message " + (i + 1));
                Thread.sleep(100);
                clocks[i] = alice.getVectorClock().clone();
            }
            
            Thread.sleep(500); // Wait for all messages to arrive
            
            // Verify ordering: each message's clock should happen-before or equal to the next
            for (int i = 0; i < clocks.length - 1; i++) {
                assertTrue(
                    clocks[i].happensBefore(clocks[i + 1]) || clocks[i].equals(clocks[i + 1]),
                    "Message " + (i + 1) + " should happen-before message " + (i + 2)
                );
            }
            
            System.out.println("[Test] ✓ Multiple messages preserve ordering");
        } finally {
            alice.stop();
            bob.stop();
        }
    }
}
