package p2p.peer.integration;

import org.junit.jupiter.api.*;
import p2p.bootstrap.BootstrapServiceImpl;
import p2p.bootstrap.UserRegistry;
import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.common.rmi.BootstrapService;
import p2p.peer.PeerController;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for group chat functionality using real invitation flow.
 * Tests invitation accept/reject, timeouts, messaging, leader election, gossip, and sync.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupChatIntegrationTest {

    private static final int BOOTSTRAP_PORT = 14099;
    private static int nextPort = 16001;
    
    private UserRegistry userRegistry;
    private BootstrapService bootstrapService;
    private Registry bootstrapRegistry;
    private List<PeerController> peers;
    
    private static synchronized int getNextPort() {
        return nextPort++;
    }
    
    @BeforeAll
    void setupBootstrap() throws Exception {
        userRegistry = new UserRegistry();
        bootstrapService = new BootstrapServiceImpl(userRegistry);
        bootstrapRegistry = LocateRegistry.createRegistry(BOOTSTRAP_PORT);
        bootstrapRegistry.rebind("BootstrapService", bootstrapService);
        
        peers = new ArrayList<>();
        System.out.println("[GroupChatTest] Bootstrap server started on port " + BOOTSTRAP_PORT);
    }
    
    @AfterAll
    void teardownBootstrap() throws Exception {
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
        
        System.out.println("[GroupChatTest] Bootstrap server stopped");
    }
    
    @AfterEach
    void cleanupPeers() throws Exception {
        for (PeerController peer : peers) {
            try {
                peer.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        peers.clear();
        Thread.sleep(500); // Give RMI time to clean up
    }
    
    private PeerController createPeer(String username, int port) throws Exception {
        PeerController peer = new PeerController(username, port, "localhost", BOOTSTRAP_PORT);
        peer.start();
        peers.add(peer);
        Thread.sleep(200);
        return peer;
    }
    
    private void makeFriends(PeerController p1, PeerController p2) throws Exception {
        p1.sendFriendRequest(p2.getLocalUser().getUsername());
        Thread.sleep(100);
        p2.acceptFriendRequest(p1.getLocalUser().getUsername());
        Thread.sleep(100);
    }
    
    // ==========================================================================
    // TEST 1: Successful group creation with invitations accepted
    // ==========================================================================
    
    @Test
    @DisplayName("1. Successful group creation with invitation acceptance and messaging")
    void testSuccessfulGroupCreationAndMessaging() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        
        // Set auto-accept handlers for all
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        
        // Establish friendships (required for group invitations)
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);
        
        // Alice creates group and sends invitations
        Group tempGroup = alice.createGroup("ChatGroup", List.of("Bob", "Charlie"));
        assertNotNull(tempGroup);
        assertEquals("ChatGroup", tempGroup.getName());
        
        // Wait for invitations to be sent and auto-accepted (3s timeout + processing)
        Thread.sleep(4000);
        
        // Verify group finalized on Alice's side
        assertEquals(1, alice.getGroups().size(), "Alice should have 1 finalized group");
        String groupId = tempGroup.getGroupId();
        Group finalizedGroup = alice.getGroup(groupId);
        assertNotNull(finalizedGroup, "Alice should have the finalized group");
        
        // Verify group structure: Alice is leader, Bob & Charlie are members
        assertEquals(alice.getLocalUser().getUserId(), finalizedGroup.getLeaderId());
        assertEquals(2, finalizedGroup.getMembers().size()); // Bob & Charlie (Alice is leader, not in members)
        
        // IMPORTANT: Verify Bob and Charlie also have the group
        assertEquals(1, bob.getGroups().size(), "Bob should have received the finalized group");
        assertEquals(1, charlie.getGroups().size(), "Charlie should have received the finalized group");
        assertNotNull(bob.getGroup(groupId), "Bob should have the group");
        assertNotNull(charlie.getGroup(groupId), "Charlie should have the group");
        
        // Send message to group and verify delivery
        alice.sendGroupMessage(groupId, "Hello everyone!");
        Thread.sleep(500);
        
        // Verify message received by all
        assertTrue(alice.getGroupManager().getMessages(groupId).size() > 0);
        assertTrue(bob.getGroupManager().getMessages(groupId).size() > 0);
        assertTrue(charlie.getGroupManager().getMessages(groupId).size() > 0);
        
        System.out.println("[TEST] ✓ Test 1 passed: Group created successfully with invitations and all members can message");
    }
    
    // ==========================================================================
    // TEST 2: Group creation fails when one member rejects invitation
    // ==========================================================================
    
    @Test
    @DisplayName("2. Group creation fails when one member rejects invitation")
    void testGroupCreationWithRejection() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        
        // Set acceptance behavior: Bob accepts, Charlie rejects
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);  // Bob accepts
        charlie.setInvitationHandler(request -> false);  // Charlie REJECTS
        
        // Alice creates group
        alice.createGroup("FailGroup", List.of("Bob", "Charlie"));
        
        // Wait for invitations + responses + timeout
        Thread.sleep(4000);
        
        // With Bob accepting but Charlie rejecting, group should be dissolved (need 3 total)
        // Alice should have NO finalized groups
        assertEquals(0, alice.getGroups().size(), "Group should be dissolved due to rejection (need 3 members, only have 2)");
        assertEquals(0, bob.getGroups().size(), "Bob should not have group either");
        assertEquals(0, charlie.getGroups().size(), "Charlie rejected, should not have group");
        
        System.out.println("[TEST] ✓ Test 2 passed: Group correctly dissolved when member rejects");
    }
    
    // ==========================================================================
    // TEST 3: Invitation timeout (no handler set = default reject)
    // ==========================================================================
    
    @Test
    @DisplayName("3. Group creation fails when invitee has no handler (times out)")
    void testInvitationTimeout() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        
        // Set handlers: Alice and Bob have handlers, Charlie doesn't (will default reject)
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        // Charlie intentionally has NO handler - will reject by default
        
        // Alice creates group
        alice.createGroup("TimeoutGroup", List.of("Bob", "Charlie"));
        
        // Wait for timeout + processing
        Thread.sleep(4000);
        
        // With Charlie having no handler (auto-reject), group should dissolve
        assertEquals(0, alice.getGroups().size(), "Group should be dissolved (Charlie rejected by default)");
        assertEquals(0, bob.getGroups().size(), "Bob should not have group");
        assertEquals(0, charlie.getGroups().size(), "Charlie should not have group");
        
        System.out.println("[TEST] ✓ Test 3: Default rejection works when no handler set");
    }
    
    // ==========================================================================
    // TEST 4: Minimum group size validation
    // ==========================================================================
    
    @Test
    @DisplayName("4. Group creation requires minimum 3 members (creator + 2)")
    void testMinimumGroupSize() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        
        makeFriends(alice, bob);
        
        // Try to create group with only 1 invitee (need 2)
        try {
            alice.createGroup("SmallGroup", List.of("Bob"));
            fail("Should throw exception - need at least 2 invitees");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("at least 2"), "Error should mention minimum requirement");
        }
        
        System.out.println("[TEST] ✓ Test 4: Minimum group size validation works");
    }
    
    // ==========================================================================
    // TEST 5: Leader election on leader disconnection
    // ==========================================================================
    
    @Test
    @DisplayName("5. New leader elected when current leader disconnects")
    void testLeaderElection() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        
        // Set all as auto-accept
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);
        
        // Alice creates group
        Group tempGroup = alice.createGroup("ElectionGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000); // Wait for finalization
        
        assertEquals(1, alice.getGroups().size(), "Group should be finalized");
        String groupId = tempGroup.getGroupId();
        Group aliceGroup = alice.getGroup(groupId);
        assertNotNull(aliceGroup);
        
        // Verify Bob and Charlie have group
        assertEquals(1, bob.getGroups().size());
        assertEquals(1, charlie.getGroups().size());
        assertNotNull(bob.getGroup(groupId));
        assertNotNull(charlie.getGroup(groupId));
        
        // Verify Alice is initial leader
        assertEquals(alice.getLocalUser().getUserId(), aliceGroup.getLeaderId());
        
        // Alice disconnects
        alice.stop();
        peers.remove(alice);
        
        // Wait for heartbeat timeout and election
        Thread.sleep(8000);
        
        // Bob or Charlie should have updated their group with new leader
        Group bobGroup = bob.getGroup(groupId);
        Group charlieGroup = charlie.getGroup(groupId);
        
        // New leader should NOT be Alice
        assertNotEquals(alice.getLocalUser().getUserId(), bobGroup.getLeaderId(), 
                       "Bob's group should have new leader");
        assertNotEquals(alice.getLocalUser().getUserId(), charlieGroup.getLeaderId(),
                       "Charlie's group should have new leader");
        
        // Both should agree on same leader
        assertEquals(bobGroup.getLeaderId(), charlieGroup.getLeaderId(), 
                    "Bob and Charlie should agree on new leader");
        
        System.out.println("[TEST] ✓ Test 5: Leader election completed");
    }
    
    // ==========================================================================
    // TEST 6: Concurrent group messaging
    // ==========================================================================
    
    @Test
    @DisplayName("6. Multiple members can send messages concurrently")
    void testConcurrentMessaging() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        
        // Set all as auto-accept
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);
        
        Group  tempGroup = alice.createGroup("ConcurrentGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000);
        
        assertEquals(1, alice.getGroups().size());
        String groupId = tempGroup.getGroupId();
        
        // All three send messages concurrently
        Thread t1 = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    alice.sendGroupMessage(groupId, "Alice-" + i);
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        Thread t2 = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    bob.sendGroupMessage(groupId, "Bob-" + i);
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        Thread t3 = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    charlie.sendGroupMessage(groupId, "Charlie-" + i);
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();
        
        Thread.sleep(2000); // Wait for message propagation
        
        // Each should have received messages from all
        int aliceCount = alice.getGroupManager().getMessages(groupId).size();
        int bobCount = bob.getGroupManager().getMessages(groupId).size();
        int charlieCount = charlie.getGroupManager().getMessages(groupId).size();
        
        assertTrue(aliceCount >= 15, "Alice should have ~15 messages (got " + aliceCount + ")");
        assertTrue(bobCount >= 15, "Bob should have ~15 messages (got " + bobCount + ")");
        assertTrue(charlieCount >= 15, "Charlie should have ~15 messages (got " + charlieCount + ")");
        
        System.out.println("[TEST] ✓ Test 6: Concurrent messaging works");
    }
    
    // ==========================================================================
    // TEST 7: Gossip propagation after network partition
    // ==========================================================================
    
    @Test
    @DisplayName("7. Gossip syncs messages after network partition heals")
    void testGossipPropagation() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);
        
        Group tempGroup = alice.createGroup("GossipGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000);
        
        assertEquals(1, alice.getGroups().size());
        String groupId = tempGroup.getGroupId();
        
        // Partition: Bob offline
        bob.simulateNetworkFailure();
        
        // Alice sends messages while Bob is offline
        alice.sendGroupMessage(groupId, "Message 1");
        Thread.sleep(200);
        alice.sendGroupMessage(groupId, "Message 2");
        Thread.sleep(200);
        
        // Charlie should have messages, Bob shouldn't yet
        assertTrue(charlie.getGroupManager().getMessages(groupId).size() >= 2);
        
        // Heal partition
        bob.restoreNetwork();
        
        // Wait for gossip to propagate
        Thread.sleep(5000);
        
        // Now Bob should have caught up via gossip
        int bobMessages = bob.getGroupManager().getMessages(groupId).size();
        assertTrue(bobMessages >= 2, "Bob should have received messages via gossip (got " + bobMessages + ")");
        
        System.out.println("[TEST] ✓ Test 7: Gossip propagation works");
    }
    
    // ==========================================================================
    // TEST 8: Consensus-based sync for late joiner
    // ==========================================================================
    
    @Test
    @DisplayName("8. Late peer syncs missing messages via consensus")
    void testConsensusSync() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);
        
        Group tempGroup = alice.createGroup("SyncGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000);
        
        assertEquals(1, alice.getGroups().size());
        String groupId = tempGroup.getGroupId();
        
        // Charlie goes offline
        charlie.simulateNetworkFailure();
        
        // Send messages while Charlie is offline
        alice.sendGroupMessage(groupId, "Sync message 1");
        Thread.sleep(200);
        alice.sendGroupMessage(groupId, "Sync message 2");
        Thread.sleep(200);
        
        // Charlie comes back
        charlie.restoreNetwork();
        
        // Wait for consensus sync
        Thread.sleep(5000);
        
        // Charlie should have synced messages
        int charlieMessages = charlie.getGroupManager().getMessages(groupId).size();
        assertTrue(charlieMessages >= 2, "Charlie should have synced messages (got " + charlieMessages + ")");
        
        System.out.println("[TEST] ✓ Test 8: Consensus sync works");
    }
    
    // ==========================================================================
    // TEST 9: Concurrent group creations by different users
    // ==========================================================================
    
    @Test
    @DisplayName("9. Multiple users can create different groups concurrently")
    void testConcurrentGroupCreations() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        
        // Set all as auto-accept
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        david.setInvitationHandler(request -> true);
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);
        makeFriends(charlie, david);
        makeFriends(bob, david);
        
        // Alice creates group 1 concurrently with Charlie creating group 2
        Thread t1 = new Thread(() -> {
            try {
                alice.createGroup("Group1", List.of("Bob", "Charlie"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(100);
                charlie.createGroup("Group2", List.of("Bob", "David"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        Thread.sleep(5000); // Wait for both to finalize
        
        // Alice should have Group1
        assertEquals(1, alice.getGroups().size(), "Alice should have 1 group");
        
        // Bob should have BOTH groups (invited to both)
        assertEquals(2, bob.getGroups().size(), "Bob should have 2 groups");
        
        // Charlie should have BOTH groups (created one, invited to other)
        assertEquals(2, charlie.getGroups().size(), "Charlie should have 2 groups");
        
        // David should have Group2
        assertEquals(1, david.getGroups().size(), "David should have 1 group");
        
        System.out.println("[TEST] ✓ Test 9: Concurrent group creation works");
    }
    
    // ==========================================================================
    // TEST 10: Large group with rapid messaging
    // ==========================================================================
    
    @Test
    @DisplayName("10. Large group handles rapid fire messages correctly")
    void testLargeGroupMessaging() throws Exception {
        // Create 5 peers
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        PeerController eve = createPeer("Eve", getNextPort());
        
        // Set all as auto-accept
        List<PeerController> allPeers = List.of(alice, bob, charlie, david, eve);
        for (PeerController peer : allPeers) {
            peer.setInvitationHandler(request -> true);
        }
        
        // Make all friends
        for (int i = 0; i < allPeers.size(); i++) {
            for (int j = i + 1; j < allPeers.size(); j++) {
                makeFriends(allPeers.get(i), allPeers.get(j));
            }
        }
        
        // Alice creates large group
        Group tempGroup = alice.createGroup("LargeGroup", List.of("Bob", "Charlie", "David", "Eve"));
        Thread.sleep(5000); // Wait for all invitations
        
        // Verify all have the group
        String groupId = tempGroup.getGroupId();
        for (PeerController peer : allPeers) {
            assertEquals(1, peer.getGroups().size(), peer.getLocalUser().getUsername() + " should have the group");
            assertNotNull(peer.getGroup(groupId), peer.getLocalUser().getUsername() + " should have the group by ID");
        }
        
        // Send rapid messages
        for (int i = 0; i < 10; i++) {
            alice.sendGroupMessage(groupId, "Rapid message " + i);
            Thread.sleep(100);
        }
        
        Thread.sleep(2000); // Wait for propagation
        
        // Verify all received messages
        for (PeerController peer : allPeers) {
            int messageCount = peer.getGroupManager().getMessages(groupId).size();
            assertTrue(messageCount >= 10, peer.getLocalUser().getUsername() + 
                      " should have at least 10 messages (got " + messageCount + ")");
        }
        
        System.out.println("[TEST] ✓ Test 10: Large group messaging verified");
    }
}
