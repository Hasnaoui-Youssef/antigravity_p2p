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
    // TEST 5: Group auto-dissolution when falling below minimum size
    // ==========================================================================
    
    @Test
    @DisplayName("5. Group auto-dissolves when only 2 members remain")
    void testGroupAutoDissolution() throws Exception {
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
        
        // Alice creates group with Bob and Charlie
        Group tempGroup = alice.createGroup("DissolutionGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000); // Wait for finalization
        
        assertEquals(1, alice.getGroups().size(), "Group should be finalized");
        String groupId = tempGroup.getGroupId();
        
        // Verify all have the group
        assertEquals(1, bob.getGroups().size());
        assertEquals(1, charlie.getGroups().size());
        
        // Verify Alice is initial leader
        Group aliceGroup = alice.getGroup(groupId);
        assertEquals(alice.getLocalUser().getUserId(), aliceGroup.getLeaderId());
        
        // Alice (leader) disconnects - leaves only 2 members
        alice.stop();
        peers.remove(alice);
        
        // Wait for leader failure detection and auto-dissolution
        Thread.sleep(8000);
        
        // Group should be dissolved for Bob and Charlie (fell below minimum of 3)
        assertNull(bob.getGroup(groupId), 
            "Bob's group should be dissolved (only 2 members left)");
        assertNull(charlie.getGroup(groupId),
            "Charlie's group should be dissolved (only 2 members left)");
        
        System.out.println("[TEST] ✓ Test 5: Group auto-dissolution when below minimum size");
    }
    
    // ==========================================================================
    // TEST 6: Leader election with sufficient members (6-person group)
    // ==========================================================================
    
    @Test
    @DisplayName("6. New leader elected when current leader disconnects (6-person group)")
    void testLeaderElectionLargeGroup() throws Exception {
        // Create 6 peers
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        PeerController eve = createPeer("Eve", getNextPort());
        PeerController frank = createPeer("Frank", getNextPort());
        
        // Set all as auto-accept
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        david.setInvitationHandler(request -> true);
        eve.setInvitationHandler(request -> true);
        frank.setInvitationHandler(request -> true);
        
        // Create friendships (Alice with all others)
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(alice, eve);
        makeFriends(alice, frank);
        
        // Cross friendships for group communication
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);
        makeFriends(david, eve);
        makeFriends(eve, frank);
        
        // Alice creates group with 5 others
        Group tempGroup = alice.createGroup("LargeElectionGroup", 
            List.of("Bob", "Charlie", "David", "Eve", "Frank"));
        
        String groupId = tempGroup.getGroupId();
        
        // Use polling for all peers to receive the group
        assertTrue(bob.waitForGroup(groupId, 10000, 200), "Bob should receive group");
        assertTrue(charlie.waitForGroup(groupId, 10000, 200), "Charlie should receive group");
        assertTrue(david.waitForGroup(groupId, 10000, 200), "David should receive group");
        assertTrue(eve.waitForGroup(groupId, 10000, 200), "Eve should receive group");
        assertTrue(frank.waitForGroup(groupId, 10000, 200), "Frank should receive group");
        
        // Verify all have the group
        assertEquals(1, alice.getGroups().size(), "Alice should have group");
        assertEquals(1, bob.getGroups().size(), "Bob should have group");
        assertEquals(1, charlie.getGroups().size(), "Charlie should have group");
        assertEquals(1, david.getGroups().size(), "David should have group");
        assertEquals(1, eve.getGroups().size(), "Eve should have group");
        assertEquals(1, frank.getGroups().size(), "Frank should have group");
        
        // Verify Alice is initial leader
        Group aliceGroup = alice.getGroup(groupId);
        String oldLeaderId = alice.getLocalUser().getUserId();
        assertEquals(oldLeaderId, aliceGroup.getLeaderId());
        
        // Alice disconnects
        alice.stop();
        peers.remove(alice);
        
        // Use polling to wait for new leader election
        assertTrue(bob.waitForNewLeader(groupId, oldLeaderId, 15000, 300), 
            "Bob should see new leader elected");
        
        // Remaining 5 members should have elected a new leader
        Group bobGroup = bob.getGroup(groupId);
        Group charlieGroup = charlie.getGroup(groupId);
        Group davidGroup = david.getGroup(groupId);
        Group eveGroup = eve.getGroup(groupId);
        Group frankGroup = frank.getGroup(groupId);
        
        // All should still have the group (not dissolved - 5 members remain)
        assertNotNull(bobGroup, "Bob should still have group");
        assertNotNull(charlieGroup, "Charlie should still have group");
        assertNotNull(davidGroup, "David should still have group");
        assertNotNull(eveGroup, "Eve should still have group");
        assertNotNull(frankGroup, "Frank should still have group");
        
        // New leader should NOT be Alice
        assertNotEquals(oldLeaderId, bobGroup.getLeaderId(), 
            "Bob's group should have new leader");
        assertNotEquals(oldLeaderId, charlieGroup.getLeaderId(),
            "Charlie's group should have new leader");
        
        // All should agree on the same new leader
        String newLeader = bobGroup.getLeaderId();
        assertEquals(newLeader, charlieGroup.getLeaderId(), 
            "Bob and Charlie should agree on new leader");
        assertEquals(newLeader, davidGroup.getLeaderId(),
            "David should agree on new leader");
        assertEquals(newLeader, eveGroup.getLeaderId(),
            "Eve should agree on new leader");
        assertEquals(newLeader, frankGroup.getLeaderId(),
            "Frank should agree on new leader");
        
        // Verify epoch was incremented
        assertTrue(bobGroup.getEpoch() > 0, "Epoch should be incremented after election");
        
        System.out.println("[TEST] ✓ Test 6: Leader election completed successfully with 5 remaining members");
        System.out.println("[TEST] New leader: " + newLeader);
    }
    
    // ==========================================================================
    // TEST 7: Concurrent group messaging
    // ==========================================================================
    
    @Test
    @DisplayName("7. Multiple members can send messages concurrently")
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
        
        System.out.println("[TEST] ✓ Test 7: Concurrent messaging works");
    }
    
    // ==========================================================================
    // TEST 8: Gossip propagation after network partition
    // ==========================================================================
    
    @Test
    @DisplayName("8. Gossip syncs messages after network partition heals")
    void testGossipPropagation() throws Exception {
        // Use 4 members to avoid auto-dissolution when one goes offline
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        david.setInvitationHandler(request -> true);
        
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);
        
        Group tempGroup = alice.createGroup("GossipGroup", List.of("Bob", "Charlie", "David"));
        
        // Use polling instead of fixed sleep for group creation
        assertTrue(bob.waitForGroup(tempGroup.getGroupId(), 10000, 200), 
            "Bob should receive the group");
        assertTrue(charlie.waitForGroup(tempGroup.getGroupId(), 10000, 200), 
            "Charlie should receive the group");
        assertTrue(david.waitForGroup(tempGroup.getGroupId(), 10000, 200), 
            "David should receive the group");
        
        assertEquals(1, alice.getGroups().size());
        String groupId = tempGroup.getGroupId();
        
        // Partition: Bob offline
        bob.simulateNetworkFailure();
        
        // Alice sends messages while Bob is offline
        alice.sendGroupMessage(groupId, "Message 1");
        alice.sendGroupMessage(groupId, "Message 2");
        
        // Use polling for Charlie and David to receive messages
        assertTrue(charlie.waitForMessages(groupId, 2, 5000, 100), 
            "Charlie should have received messages");
        assertTrue(david.waitForMessages(groupId, 2, 5000, 100), 
            "David should have received messages");
        
        // Heal partition
        bob.restoreNetwork();
        
        // Use polling for Bob to sync via gossip
        assertTrue(bob.waitForMessages(groupId, 2, 10000, 200), 
            "Bob should have synced messages via gossip");
        
        int bobMessages = bob.getGroupManager().getMessages(groupId).size();
        System.out.println("[TEST] ✓ Test 8: Gossip propagation works - Bob has " + bobMessages + " messages");
    }
    
    // ==========================================================================
    // TEST 9: Consensus-based sync for multiple late joiners
    // ==========================================================================
    
    @Test
    @DisplayName("9. Multiple peers sync missing messages after staggered disconnects")
    void testConsensusSync() throws Exception {
        // Create 6 peers
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        PeerController eve = createPeer("Eve", getNextPort());
        PeerController frank = createPeer("Frank", getNextPort());
        
        alice.setInvitationHandler(request -> true);
        bob.setInvitationHandler(request -> true);
        charlie.setInvitationHandler(request -> true);
        david.setInvitationHandler(request -> true);
        eve.setInvitationHandler(request -> true);
        frank.setInvitationHandler(request -> true);
        
        // Create friendships
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(alice, eve);
        makeFriends(alice, frank);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);
        makeFriends(david, eve);
        makeFriends(eve, frank);
        // Add more friendships to ensure sync quorum can reach Bob/Frank
        makeFriends(bob, eve);
        makeFriends(bob, frank);
        makeFriends(charlie, frank);
        
        Group tempGroup = alice.createGroup("SyncGroup", List.of("Bob", "Charlie", "David", "Eve", "Frank"));
        
        // Use polling for all peers to receive the group
        assertTrue(bob.waitForGroup(tempGroup.getGroupId(), 10000, 200), "Bob should receive group");
        assertTrue(charlie.waitForGroup(tempGroup.getGroupId(), 10000, 200), "Charlie should receive group");
        assertTrue(david.waitForGroup(tempGroup.getGroupId(), 10000, 200), "David should receive group");
        assertTrue(eve.waitForGroup(tempGroup.getGroupId(), 10000, 200), "Eve should receive group");
        assertTrue(frank.waitForGroup(tempGroup.getGroupId(), 10000, 200), "Frank should receive group");
        
        assertEquals(1, alice.getGroups().size());
        String groupId = tempGroup.getGroupId();
        
        // Stagger disconnects: Charlie, David, and Eve go offline at different times
        charlie.simulateNetworkFailure();
        david.simulateNetworkFailure();
        eve.simulateNetworkFailure();
        Thread.sleep(300); // Let them fully disconnect
        
        // Send all messages while all 3 are offline
        alice.sendGroupMessage(groupId, "Message 1 - All 3 offline");
        alice.sendGroupMessage(groupId, "Message 2 - All 3 offline");
        alice.sendGroupMessage(groupId, "Message 3 - All 3 offline");
        
        // Use polling for Bob and Frank to receive messages
        assertTrue(bob.waitForMessages(groupId, 3, 5000, 100), "Bob should have all 3 messages");
        assertTrue(frank.waitForMessages(groupId, 3, 5000, 100), "Frank should have all 3 messages");
        
        // Restore all 3 peers one at a time with gaps
        charlie.restoreNetwork();
        Thread.sleep(500);
        david.restoreNetwork();
        Thread.sleep(500);
        eve.restoreNetwork();
        
        // Use polling with longer timeout for all 3 to sync messages
        // The gossip cycle runs every 1 second and might need several rounds
        assertTrue(charlie.waitForMessages(groupId, 3, 20000, 500), 
            "Charlie should have synced all messages");
        assertTrue(david.waitForMessages(groupId, 3, 20000, 500), 
            "David should have synced all messages");
        assertTrue(eve.waitForMessages(groupId, 3, 20000, 500), 
            "Eve should have synced all messages");
        
        int charlieMessages = charlie.getGroupManager().getMessages(groupId).size();
        int davidMessages = david.getGroupManager().getMessages(groupId).size();
        int eveMessages = eve.getGroupManager().getMessages(groupId).size();
        
        System.out.println("[TEST] ✓ Test 9: Staggered sync works - Charlie: " + charlieMessages + 
            ", David: " + davidMessages + ", Eve: " + eveMessages);
    }
    
    // ==========================================================================
    // TEST 10: Concurrent group creations by different users
    // ==========================================================================
    
    @Test
    @DisplayName("11. Multiple users can create different groups concurrently")
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
        
        System.out.println("[TEST] ✓ Test 11: Concurrent group creation works");
    }
    
    // ==========================================================================
    // TEST 11: Large group with rapid messaging
    // ==========================================================================
    
    @Test
    @DisplayName("11. Large group handles rapid fire messages correctly")
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
        
        System.out.println("[TEST] ✓ Test 11: Large group messaging verified");
    }
}
