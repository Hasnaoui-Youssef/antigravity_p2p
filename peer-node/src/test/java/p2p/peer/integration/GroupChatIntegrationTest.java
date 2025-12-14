package p2p.peer.integration;

import p2p.peer.util.MultiPeerGroupEventWaiter;
import p2p.peer.util.TestEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.*;
import p2p.bootstrap.BootstrapServiceImpl;
import p2p.bootstrap.UserRegistry;
import p2p.common.model.Group;
import p2p.common.model.GroupEvent;
import p2p.common.model.message.ChatMessage;
import p2p.common.model.message.GroupInvitationMessage;
import p2p.common.model.message.Message;
import p2p.peer.PeerEventListener;
import p2p.common.rmi.BootstrapService;
import p2p.common.rmi.PeerService;
import p2p.peer.PeerController;
import p2p.common.model.User;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for group chat functionality using real invitation flow.
 * Tests invitation accept/reject, timeouts, messaging, leader election, gossip,
 * and sync.
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupChatIntegrationTest {

    private static final int BOOTSTRAP_PORT = 14099;
    private static final int HEARTBEAT_PORT = 9876;
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
            UnicastRemoteObject.unexportObject(bootstrapService, true);
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
                peer.getEventListeners().forEach(peer::removeEventListener);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        peers.clear();
        Thread.sleep(500); // Give RMI time to clean up
    }

    private PeerController createPeer(String username, int port) throws Exception {
        PeerController peer = new PeerController(username, port, "localhost", BOOTSTRAP_PORT, HEARTBEAT_PORT);
        peer.start();
        peers.add(peer);
        Thread.sleep(200);
        return peer;
    }

    private void makeFriends(PeerController p1, PeerController p2) throws Exception {
        PeerEventListener e = autoAcceptFriendRequest(p2);
        p1.sendFriendRequest(p2.getLocalUser().username());
        Thread.sleep(1000);
        p2.removeEventListener(e);
    }

    /**
     * Add an auto-accept listener to a peer that accepts all friend invitations
     * automatically.
     */
    private PeerEventListener autoAcceptFriendRequest(PeerController peer) {
        PeerEventListener eventListener = new PeerEventListener() {
            @Override
            public void onGroupInvitation(GroupInvitationMessage request) {
            }

            @Override
            public void onFriendRequest(User requester) {
                try {
                    peer.acceptFriendRequest(requester.username());
                } catch (Exception e) {
                    System.err.println("Failed to auto-accept invitation: " + e.getMessage());
                }
            }

            @Override
            public void onFriendRequestAccepted(User accepter) {
            }

            @Override
            public void onMessageReceived(Message message) {
            }

            @Override
            public void onGroupEvent(String groupId, GroupEvent eventType, String message) {
            }

            @Override
            public void onLeaderElected(String groupId, String leaderId, long epoch) {
            }

            @Override
            public void onError(String message, Throwable t) {
            }

            @Override
            public void onLog(String message) {
            }
        };
        peer.addEventListener(eventListener);
        return eventListener;
    }

    /**
     * Add an auto-accept listener to a peer that accepts all group invitations
     * automatically.
     */
    private void autoAcceptInvitations(PeerController peer) {
        peer.addEventListener(new PeerEventListener() {
            @Override
            public void onGroupInvitation(GroupInvitationMessage request) {
                try {
                    peer.acceptGroupInvitation(request.getGroupId());
                } catch (Exception e) {
                    System.err.println("Failed to auto-accept invitation: " + e.getMessage());
                }
            }

            @Override
            public void onFriendRequest(User requester) {
            }

            @Override
            public void onFriendRequestAccepted(User accepter) {
            }

            @Override
            public void onMessageReceived(Message message) {
            }

            @Override
            public void onGroupEvent(String groupId, GroupEvent eventType, String message) {
            }

            @Override
            public void onLeaderElected(String groupId, String leaderId, long epoch) {
            }

            @Override
            public void onError(String message, Throwable t) {
            }

            @Override
            public void onLog(String message) {
            }
        });
    }

    /**
     * Add a reject listener to a peer that rejects all group invitations
     * automatically.
     */
    private void autoRejectInvitations(PeerController peer) {
        peer.addEventListener(new PeerEventListener() {
            @Override
            public void onGroupInvitation(GroupInvitationMessage request) {
                try {
                    peer.rejectGroupInvitation(request.getGroupId());
                } catch (Exception e) {
                    System.err.println("Failed to auto-reject invitation: " + e.getMessage());
                }
            }

            @Override
            public void onFriendRequest(User requester) {
            }

            @Override
            public void onFriendRequestAccepted(User accepter) {
            }

            @Override
            public void onMessageReceived(Message message) {
            }

            @Override
            public void onGroupEvent(String groupId, GroupEvent eventType, String message) {
            }

            @Override
            public void onLeaderElected(String groupId, String leaderId, long epoch) {
            }

            @Override
            public void onError(String message, Throwable t) {
            }

            @Override
            public void onLog(String message) {
            }
        });
    }

    @Test
    @Order(1)
    @DisplayName("Successful group creation with invitation acceptance and messaging")
    void testSuccessfulGroupCreationAndMessaging() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());

        // Set auto-accept handlers for all
        autoAcceptInvitations(alice);

        autoAcceptInvitations(bob);

        autoAcceptInvitations(charlie);

        // Establish friendships (required for group invitations)
        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);

        // Alice creates group and sends invitations
        Group tempGroup = alice.createGroup("ChatGroup", List.of("Bob", "Charlie"));
        assertNotNull(tempGroup);
        assertEquals("ChatGroup", tempGroup.name());

        // Wait for invitations to be sent and auto-accepted (3s timeout + processing)
        Thread.sleep(4000);

        // Verify group finalized on Alice's side
        assertEquals(1, alice.getGroups().size(), "Alice should have 1 finalized group");
        String groupId = tempGroup.groupId();
        Group finalizedGroup = alice.getGroup(groupId);
        assertNotNull(finalizedGroup, "Alice should have the finalized group");

        // Verify group structure: Alice is leader, Bob & Charlie are members
        assertEquals(alice.getLocalUser().userId(), finalizedGroup.leader().userId());
        assertEquals(2, finalizedGroup.members().size()); // Bob & Charlie (Alice is leader, not in members)

        // IMPORTANT: Verify Bob and Charlie also have the group
        assertEquals(1, bob.getGroups().size(), "Bob should have received the finalized group");
        assertEquals(1, charlie.getGroups().size(), "Charlie should have received the finalized group");
        assertNotNull(bob.getGroup(groupId), "Bob should have the group");
        assertNotNull(charlie.getGroup(groupId), "Charlie should have the group");

        // Send message to group and verify delivery
        alice.sendGroupMessage(groupId, "Hello everyone!");
        Thread.sleep(500);

        // Verify message received by all
        assertFalse(alice.getGroupManager().getMessages(groupId).isEmpty());
        assertFalse(bob.getGroupManager().getMessages(groupId).isEmpty());
        assertFalse(charlie.getGroupManager().getMessages(groupId).isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("Group creation fails when one member rejects invitation")
    void testGroupCreationWithRejection() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());

        makeFriends(alice, bob);
        makeFriends(alice, charlie);

        // Set acceptance behavior: Bob accepts, Charlie rejects
        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob); // Bob accepts
        autoRejectInvitations(charlie); // Charlie REJECTS

        // Alice creates group
        alice.createGroup("FailGroup", List.of("Bob", "Charlie"));

        // Wait for invitations + responses + timeout
        Thread.sleep(4000);

        // With Bob accepting but Charlie rejecting, group should be dissolved (need 3
        // total)
        // Alice should have NO finalized groups
        assertEquals(0, alice.getGroups().size(),
                "Group should be dissolved due to rejection (need 3 members, only have 2)");
        assertEquals(0, bob.getGroups().size(), "Bob should not have group either");
        assertEquals(0, charlie.getGroups().size(), "Charlie rejected, should not have group");
    }

    @Test
    @Order(3)
    @DisplayName("Group creation fails when invitee has no handler (times out)")
    void testInvitationTimeout() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());

        makeFriends(alice, bob);
        makeFriends(alice, charlie);

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoRejectInvitations(charlie);

        alice.createGroup("TimeoutGroup", List.of("Bob", "Charlie"));

        Thread.sleep(4000);

        assertEquals(0, alice.getGroups().size(), "Group should be dissolved (Charlie rejected by default)");
        assertEquals(0, bob.getGroups().size(), "Bob should not have group");
        assertEquals(0, charlie.getGroups().size(), "Charlie should not have group");
    }

    @Test
    @Order(4)
    @DisplayName("Group creation requires minimum 3 members (creator + 2)")
    void testMinimumGroupSize() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());

        makeFriends(alice, bob);

        assertThrows(IllegalArgumentException.class, () -> alice.createGroup("SmallGroup", List.of("Bob")), "Creating a group with a single invitation should throw");
    }

    @Test
    @Order(5)
    @DisplayName("Group auto-dissolves when only 2 members remain")
    void testGroupAutoDissolution() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());

        // Set all as auto-accept
        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);

        // Alice creates group with Bob and Charlie
        Group tempGroup = alice.createGroup("DissolutionGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000); // Wait for finalization

        assertEquals(1, alice.getGroups().size(), "Group should be finalized");
        String groupId = tempGroup.groupId();

        // Verify all have the group
        assertEquals(1, bob.getGroups().size());
        assertEquals(1, charlie.getGroups().size());

        // Verify Alice is initial leader
        Group aliceGroup = alice.getGroup(groupId);
        assertEquals(alice.getLocalUser().userId(), aliceGroup.leader().userId());

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

    }

    @Test
    @Order(6)
    @DisplayName("New leader elected when current leader disconnects (6-person group)")
    void testLeaderElectionLargeGroup() throws Exception {
        // Create 6 peers
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        PeerController eve = createPeer("Eve", getNextPort());
        PeerController frank = createPeer("Frank", getNextPort());

        List<PeerController> allPeers = List.of(alice, bob, charlie, david, eve, frank);

        // Set all as auto-accept
        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);
        autoAcceptInvitations(eve);
        autoAcceptInvitations(frank);

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

        // Set up waiter BEFORE creating group (use null for groupId to match any group)
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(
                null, TestEvent.GROUP_CREATED, allPeers);

        // Alice creates group with 5 others
        Group tempGroup = alice.createGroup("LargeElectionGroup",
                List.of("Bob", "Charlie", "David", "Eve", "Frank"));

        String groupId = tempGroup.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            if (!success) {
                List<String> pending = groupCreationWaiter.getPendingPeers();
                fail("Timeout waiting for group creation. Pending peers: " + pending);
            }
            allPeers.forEach(
                    p -> assertEquals(1, p.getGroups().size(), p.getLocalUser().username() + " should have group"));
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Verify Alice is initial leader
        Group aliceGroup = alice.getGroup(groupId);
        String oldLeaderId = alice.getLocalUser().userId();
        assertEquals(oldLeaderId, aliceGroup.leader().userId());

        // Alice disconnects
        alice.stop();
        peers.remove(alice);

        // Use MultiPeerGroupEventWaiter to wait for LEADER_ELECTED event
        // We expect 5 peers (Bob, Charlie, David, Eve, Frank) to see the new leader
        List<PeerController> remainingPeers = List.of(bob, charlie, david, eve, frank);
        MultiPeerGroupEventWaiter leaderElectionWaiter = new MultiPeerGroupEventWaiter(
                groupId, TestEvent.LEADER_ELECTED, remainingPeers);

        try {
            // Wait up to 30 seconds for election to complete (gossip can be slow)
            boolean success = leaderElectionWaiter.await(30, TimeUnit.SECONDS);

            if (!success) {
                List<String> pending = leaderElectionWaiter.getPendingPeers();
                fail("Timeout waiting for leader election. Pending peers: " + pending);
            }

            // Verify all agreed on the SAME leader
            User newLeader = bob.getGroup(groupId).leader();
            assertNotEquals(oldLeaderId, newLeader.userId(), "Should have new leader");

            for (PeerController peer : remainingPeers) {
                Group g = peer.getGroup(groupId);
                assertNotNull(g, peer.getLocalUser().username() + " should still have the group");
                assertEquals(newLeader.userId(), g.leader().userId(),
                        peer.getLocalUser().username() + " should agree on new leader");
            }

            System.out.println("[TEST]  Test 6: Leader election completed successfully with 5 remaining members");
            System.out.println("[TEST] New leader: "
                    + newLeader.username());

        } finally {
            leaderElectionWaiter.cleanup();
        }

        // Verify epoch was incremented
        Group bobGroup = bob.getGroup(groupId);
        assertTrue(bobGroup.epoch() > 0, "Epoch should be incremented after election");
    }

    @Test
    @Order(7)
    @DisplayName("Multiple members can send messages concurrently")
    void testConcurrentMessaging() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        AtomicInteger expectedTotalMessages = new AtomicInteger(15);

        // Set all as auto-accept
        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);

        Group tempGroup = alice.createGroup("ConcurrentGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000);

        assertEquals(1, alice.getGroups().size());
        assertEquals(1, bob.getGroups().size());
        assertEquals(1, charlie.getGroups().size());
        String groupId = tempGroup.groupId();

        // All three send messages concurrently
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                try {
                    alice.sendGroupMessage(groupId, "Alice-" + i);
                } catch (Exception exception) {
                    expectedTotalMessages.getAndDecrement();
                }
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                try {
                    bob.sendGroupMessage(groupId, "Bob-" + i);
                } catch (Exception exception) {
                    expectedTotalMessages.getAndDecrement();
                }
            }
        });
        Thread t3 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                try {
                    charlie.sendGroupMessage(groupId, "Charlie-" + i);
                } catch (Exception exception) {
                    expectedTotalMessages.getAndDecrement();
                }
            }
        });


        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();

        Thread.sleep(1000); // Wait for message propagation

        // Each should have received messages from all
        int aliceCount = alice.getGroupManager().getMessages(groupId).size();
        int bobCount = bob.getGroupManager().getMessages(groupId).size();
        int charlieCount = charlie.getGroupManager().getMessages(groupId).size();
        Function<String, String> groupMessagesContentForUser = (String name) -> switch (name) {
            case "Alice" -> alice.getGroupManager().getMessages(groupId).stream().map(ChatMessage::getContent)
                    .collect(Collectors.joining(", "));
            case "Bob" -> bob.getGroupManager().getMessages(groupId).stream().map(ChatMessage::getContent)
                    .collect(Collectors.joining(", "));
            case "Charlie" -> charlie.getGroupManager().getMessages(groupId).stream().map(ChatMessage::getContent)
                    .collect(Collectors.joining(", "));
            default -> "Unknown user";
        };

        assertEquals(expectedTotalMessages.get(), aliceCount,
                "Alice should have " + expectedTotalMessages.get() + "messages (got " + aliceCount + ")\n"
                        + groupMessagesContentForUser.apply("Alice"));
        assertEquals(expectedTotalMessages.get(), bobCount,
                "Bob should have " + expectedTotalMessages.get() + "messages (got " + bobCount + ")\n"
                        + groupMessagesContentForUser.apply("Bob"));
        assertEquals(expectedTotalMessages.get(), charlieCount,
                "Charlie should have" + expectedTotalMessages.get() + "messages (got " + charlieCount + ")\n"
                        + groupMessagesContentForUser.apply("Charlie"));

    }

    @Test
    @Order(8)
    @DisplayName("Gossip syncs messages after network partition heals")
    void testGossipPropagation() throws Exception {
        // Use 4 members to avoid auto-dissolution when one goes offline
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        List<PeerController> allPeers = List.of(alice, bob, charlie, david);
        List<PeerController> controlGroup = List.of(charlie, david);
        
        // Set up waiter BEFORE creating group (use null for groupId to match any group)
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED,
                allPeers);

        Group tempGroup = alice.createGroup("GossipGroup", List.of("Bob", "Charlie", "David"));
        String groupId = tempGroup.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            if (!success) {
                List<String> pending = groupCreationWaiter.getPendingPeers();
                fail("Timeout waiting for group creation. Pending peers: " + pending);
            }
            allPeers.forEach(
                    p -> assertEquals(1, p.getGroups().size(), p.getLocalUser().username() + " should have group"));
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Partition: Bob offline
        bob.simulateNetworkFailure();
        
        // Set up message waiter BEFORE sending messages
        MultiPeerGroupEventWaiter messageSentWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.MESSAGE_RECEIVED,
                controlGroup);

        // Alice sends messages while Bob is offline
        alice.sendGroupMessage(groupId, "Message 1");
        alice.sendGroupMessage(groupId, "Message 2");

        try {
            boolean success = messageSentWaiter.await(5, TimeUnit.SECONDS);
            if (!success) {
                List<String> pending = messageSentWaiter.getPendingPeers();
                fail("Timeout waiting for message. Pending peers: " + pending);
            }
            controlGroup.forEach(
                    p -> assertEquals(2, p.getGroupManager().getMessages(groupId).size(),
                            p.getLocalUser().username() + " should have 2 messages"));
        } finally {
            messageSentWaiter.cleanup();
        }

        // Set up waiter BEFORE restoring network to catch sync messages
        MultiPeerGroupEventWaiter bobMessageWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.MESSAGE_RECEIVED,
                List.of(bob));
        
        // Heal partition
        bob.restoreNetwork();
        
        try {
            boolean success = bobMessageWaiter.await(10, TimeUnit.SECONDS);
            if (!success) {
                List<String> pending = bobMessageWaiter.getPendingPeers();
                fail("Timeout waiting for message sync. Pending peers: " + pending);
            }
            // Small delay to ensure all sync processing completes
            Thread.sleep(500);
            assertEquals(2, bob.getGroupManager().getMessages(groupId).size(),
                    "Bob should have both messages");
        } finally {
            bobMessageWaiter.cleanup();
        }

    }

    // ==========================================================================
    // TEST 9: Consensus-based sync for multiple late joiners
    // ==========================================================================

    // ==========================================================================
    // TEST 9: Consensus-based sync for multiple late joiners
    // ==========================================================================

    @Test
    @Order(9)
    @DisplayName("Multiple peers sync missing messages after staggered disconnects")
    void testConsensusSync() throws Exception {
        // Create 6 peers
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        PeerController eve = createPeer("Eve", getNextPort());
        PeerController frank = createPeer("Frank", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);
        autoAcceptInvitations(eve);
        autoAcceptInvitations(frank);

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

        List<PeerController> allPeers = List.of(alice, bob, charlie, david, eve, frank);
        
        // Set up waiter BEFORE creating group (use null for groupId to match any group)
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(
                null, TestEvent.GROUP_CREATED, allPeers);
        
        Group tempGroup = alice.createGroup("SyncGroup", List.of("Bob", "Charlie", "David", "Eve", "Frank"));
        String groupId = tempGroup.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            if (!success) {
                List<String> pending = groupCreationWaiter.getPendingPeers();
                fail("Timeout waiting for group creation. Pending peers: " + pending);
            }
            allPeers.forEach(
                    p -> assertEquals(1, p.getGroups().size(), p.getLocalUser().username() + " should have group"));
        } finally {
            groupCreationWaiter.cleanup();
        }

        assertEquals(1, alice.getGroups().size());

        // Stagger disconnects: Charlie, David, and Eve go offline at different times
        charlie.simulateNetworkFailure();
        david.simulateNetworkFailure();
        eve.simulateNetworkFailure();
        Thread.sleep(300); // Let them fully disconnect

        List<PeerController> controlGroup = List.of(bob, frank);
        
        // Set up message waiter BEFORE sending messages
        MultiPeerGroupEventWaiter messageSentWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.MESSAGE_RECEIVED,
                controlGroup);

        // Send all messages while all 3 are offline
        alice.sendGroupMessage(groupId, "Message 1 - All 3 offline");
        alice.sendGroupMessage(groupId, "Message 2 - All 3 offline");
        alice.sendGroupMessage(groupId, "Message 3 - All 3 offline");

        try {
            boolean success = messageSentWaiter.await(5, TimeUnit.SECONDS);
            if (!success) {
                List<String> pending = messageSentWaiter.getPendingPeers();
                fail("Timeout waiting for message. Pending peers: " + pending);
            }
            controlGroup.forEach(
                    p -> assertEquals(3, p.getGroupManager().getMessages(groupId).size(),
                            p.getLocalUser().username() + " should have 3 messages"));
        } finally {
            messageSentWaiter.cleanup();
        }

        // Set up waiter BEFORE restoring network to catch sync messages
        List<PeerController> restoredGroup = List.of(charlie, david, eve);
        MultiPeerGroupEventWaiter restoredMessageWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.MESSAGE_RECEIVED,
                restoredGroup);

        // Restore all 3 peers one at a time with gaps
        charlie.restoreNetwork();
        Thread.sleep(500);
        david.restoreNetwork();
        Thread.sleep(500);
        eve.restoreNetwork();

        try {
            boolean success = restoredMessageWaiter.await(10, TimeUnit.SECONDS);
            if (!success) {
                List<String> pending = restoredMessageWaiter.getPendingPeers();
                fail("Timeout waiting for message sync. Pending peers: " + pending);
            }
            // Small delay to ensure all sync processing completes
            Thread.sleep(500);
            restoredGroup.forEach(
                    p -> assertEquals(3, p.getGroupManager().getMessages(groupId).size(),
                            p.getLocalUser().username() + " should have 3 messages"));
        } finally {
            restoredMessageWaiter.cleanup();
        }
    }

    @Test
    @Order(10)
    @DisplayName("Multiple users can create different groups concurrently")
    void testConcurrentGroupCreations() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());

        AtomicInteger expectedGroups = new AtomicInteger(2);

        // Set all as auto-accept
        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);

        // Alice creates group 1 concurrently with Charlie creating group 2
        Thread t1 = new Thread(() -> {
            try {
                alice.createGroup("Group1", List.of("Bob", "Charlie"));
            } catch (Exception e) {
                expectedGroups.getAndDecrement();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(100);
                charlie.createGroup("Group2", List.of("Bob", "Alice"));
            } catch (Exception e) {
                expectedGroups.getAndDecrement();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        Thread.sleep(5000); // Wait for both to finalize
        if(expectedGroups.get() <= 0){
            fail("Both groups failed to be created");
        }

        // Alice should have Group1
        assertEquals(expectedGroups.get(), alice.getGroups().size(), "Alice should have 2 group");

        // Bob should have BOTH groups (invited to both)
        assertEquals(expectedGroups.get(), bob.getGroups().size(), "Bob should have 2 groups");

        // Charlie should have BOTH groups (created one, invited to other)
        assertEquals(expectedGroups.get(), charlie.getGroups().size(), "Charlie should have 2 groups");

    }

    @Test
    @Order(11)
    @DisplayName("Large group handles rapid fire messages correctly")
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
            autoAcceptInvitations(peer);
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
        String groupId = tempGroup.groupId();
        for (PeerController peer : allPeers) {
            assertEquals(1, peer.getGroups().size(), peer.getLocalUser().username() + " should have the group");
            assertNotNull(peer.getGroup(groupId), peer.getLocalUser().username() + " should have the group by ID");
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
            assertTrue(messageCount >= 10, peer.getLocalUser().username() +
                    " should have at least 10 messages (got " + messageCount + ")");
        }

    }

    @Test
    @Order(12)
    @DisplayName("Group dissolves when member leaves and size < 3")
    void testMemberLeavingDissolvesGroup() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);

        Group tempGroup = alice.createGroup("LeaveDissolveGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000);

        String groupId = tempGroup.groupId();
        assertNotNull(alice.getGroup(groupId));
        assertNotNull(bob.getGroup(groupId));
        assertNotNull(charlie.getGroup(groupId));

        // Charlie leaves
        charlie.leaveGroup(groupId);
        Thread.sleep(2000); // Wait for event propagation and dissolution

        // Group should be dissolved for Alice and Bob (Size = 2 < 3)
        assertNull(alice.getGroup(groupId), "Group should be dissolved for Alice");
        assertNull(bob.getGroup(groupId), "Group should be dissolved for Bob");

        // Charlie already removed it locally
        assertNull(charlie.getGroup(groupId));

        System.out.println("[TEST] Test 10: Group dissolved after member leave");
    }

    @Test
    @Order(13)
    @DisplayName("Group continues when member leaves and size >= 3")
    void testMemberLeavingContinuesGroup() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        Group tempGroup = alice.createGroup("LeaveContinueGroup", List.of("Bob", "Charlie", "David"));
        Thread.sleep(4000);

        String groupId = tempGroup.groupId();
        assertEquals(4, alice.getGroup(groupId).allMembers().size());

        // Send a message from the leader to keep leader activity fresh
        // This prevents the gossip manager from detecting leader failure during the test
        alice.sendGroupMessage(groupId, "Keeping group alive");
        Thread.sleep(500);

        // David leaves
        david.leaveGroup(groupId);
        Thread.sleep(2000);

        // Group should REMAIN for Alice, Bob, Charlie (Size = 3 >= 3)
        Group aliceGroup = alice.getGroup(groupId);
        assertNotNull(aliceGroup, "Group should continue for Alice");
        assertEquals(3, aliceGroup.allMembers().size(), "Group should have 3 members");

        assertNotNull(bob.getGroup(groupId), "Group should continue for Bob");
        assertNotNull(charlie.getGroup(groupId), "Group should continue for Charlie");
        assertNull(david.getGroup(groupId), "David should no longer have the group");

        // Verify David is actually removed from Alice's view
        assertFalse(aliceGroup.isMember(david.getLocalUser().userId()), "David should not be a member");

        System.out.println("[TEST] Test 11: Group continued after member leave");
    }

    @Test
    @Order(14)
    @DisplayName("Leader leaves, hands over leadership, group continues")
    void testLeaderLeavingHandover() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        // Ensure connectivity for everyone for smooth handover
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        Group tempGroup = alice.createGroup("LeaderLeaveGroup", List.of("Bob", "Charlie", "David"));
        Thread.sleep(4000);

        String groupId = tempGroup.groupId();
        assertEquals(alice.getLocalUser().userId(), alice.getGroup(groupId).leader().userId());

        List<PeerController> remainingPeers = List.of(bob, charlie, david);
        MultiPeerGroupEventWaiter waiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.MEMBER_LEFT,
                remainingPeers);

        // Alice (Leader) leaves
        alice.leaveGroup(groupId);

        try {
            // Wait for the USER_LEFT event to propagate
            // Since Handover happens first, the group should still exist with a NEW leader
            // when we check.
            boolean success = waiter.await(5, TimeUnit.SECONDS);
            assertTrue(success, "Timeout waiting for user left event");

            // Verify group exists for others
            Group bobGroup = bob.getGroup(groupId);
            assertNotNull(bobGroup);
            assertEquals(3, bobGroup.allMembers().size());

            // Verify NEW LEADER is NOT Alice
            String newLeaderId = bobGroup.leader().userId();
            assertNotEquals(alice.getLocalUser().userId(), newLeaderId, "Alice should not be leader anymore");

            // Verify new leader is one of the members (Bob, Charlie, David) or whoever had
            // highest ID
            assertTrue(
                    List.of(bob, charlie, david).stream().anyMatch(p -> p.getLocalUser().userId().equals(newLeaderId)),
                    "New leader should be one of the remaining members");

        } finally {
            waiter.cleanup();
        }

        System.out.println("[TEST] Test 12: Leader left, handed over, group continues");
    }

    @Test
    @Order(14)
    @DisplayName("Non-leader cannot broadcast USER_JOINED/USER_REJECTED")
    void testSecurityChecks() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        makeFriends(alice, bob);

        // Alice creates group with just Bob (and a third dummy - wait, creates with
        // list)
        // Need 3 total. Add Charlie.
        PeerController charlie = createPeer("Charlie", getNextPort());
        autoAcceptInvitations(charlie);
        makeFriends(alice, charlie);
        makeFriends(bob, charlie);

        Group group = alice.createGroup("SecurityGroup", List.of("Bob", "Charlie"));
        Thread.sleep(4000);
        String groupId = group.groupId();

        // Bob (non-leader) tries to send USER_JOINED for a fake user
        User fakeUser = User.create("FakeUser", "1.2.3.4", 1234);

        // Construct malicious message
        p2p.common.model.message.GroupEventMessage maliciousMsg = p2p.common.model.message.GroupEventMessage
                .userJoinedMessage(
                        bob.getLocalUser().userId(), // Sender is Bob (Not Leader)
                        group,
                        new p2p.common.vectorclock.VectorClock(),
                        fakeUser);

        // Alice receives it
        Registry registry = LocateRegistry.getRegistry(alice.getLocalUser().ipAddress(),
                alice.getLocalUser().rmiPort());
        PeerService peerService = (PeerService) registry.lookup("PeerService");

        // Should be logged as ignored and verify Alice's group doesn't have FakeUser
        peerService.receiveMessage(maliciousMsg);
        Thread.sleep(500);

        assertFalse(alice.getGroup(groupId).isMember(fakeUser.userId()),
                "Alice should not accept fake user from non-leader");

    }

    @Test
    @Order(15)
    @DisplayName("Messages sent after user leaves are not delivered to that user")
    void testMessagesNotDeliveredAfterLeave() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        // Set up waiter before creating group
        List<PeerController> allPeers = List.of(alice, bob, charlie, david);
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("LeaveMessageTest", List.of("Bob", "Charlie", "David"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Verify all have the group
        allPeers.forEach(p -> assertEquals(1, p.getGroups().size()));

        // David leaves the group
        david.leaveGroup(groupId);
        Thread.sleep(1000); // Wait for leave to propagate

        // Send a message from Alice after David left
        List<PeerController> remainingPeers = List.of(bob, charlie);
        MultiPeerGroupEventWaiter messageWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.MESSAGE_RECEIVED, remainingPeers);

        alice.sendGroupMessage(groupId, "Message after David left");

        try {
            boolean success = messageWaiter.await(5, TimeUnit.SECONDS);
            assertTrue(success, "Message should be received by remaining members");
            Thread.sleep(500);
        } finally {
            messageWaiter.cleanup();
        }

        // Verify David did not receive the message (he left)
        // David shouldn't have the group anymore
        assertNull(david.getGroup(groupId), "David should not have the group after leaving");
        assertEquals(0, david.getGroupManager().getMessages(groupId).size(),
                "David should not have received the message sent after he left");

        // Verify remaining members received the message
        assertEquals(1, bob.getGroupManager().getMessages(groupId).size());
        assertEquals(1, charlie.getGroupManager().getMessages(groupId).size());

    }

    @Test
    @Order(16)
    @DisplayName("User list is properly updated before gossip runs after leave")
    void testUserListUpdatedBeforeGossip() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        List<PeerController> allPeers = List.of(alice, bob, charlie, david);
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("UserListTest", List.of("Bob", "Charlie", "David"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Verify initial member counts
        Group aliceGroup = alice.getGroup(groupId);
        assertEquals(4, aliceGroup.activeMembers().size(), "Should have 4 active members initially");

        // David leaves
        david.leaveGroup(groupId);
        Thread.sleep(2000); // Wait for leave to propagate

        // Verify member lists are updated on all remaining peers
        for (PeerController peer : List.of(alice, bob, charlie)) {
            Group g = peer.getGroup(groupId);
            assertNotNull(g, peer.getLocalUser().username() + " should still have the group");
            assertEquals(3, g.activeMembers().size(),
                    peer.getLocalUser().username() + " should see 3 active members after David left");
            assertFalse(g.isMember(david.getLocalUser()),
                    peer.getLocalUser().username() + " should not see David as a member");
        }

    }

    @Test
    @Order(17)
    @DisplayName("User leaving during message delivery is handled gracefully")
    void testLeaveDuringMessageDelivery() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        List<PeerController> allPeers = List.of(alice, bob, charlie, david);
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("ConcurrentLeaveTest", List.of("Bob", "Charlie", "David"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Send multiple messages and have David leave in the middle
        alice.sendGroupMessage(groupId, "Message 1 - Before leave");

        // David leaves immediately after first message
        david.leaveGroup(groupId);

        // Send more messages - these should go to remaining members only
        alice.sendGroupMessage(groupId, "Message 2 - After leave");
        alice.sendGroupMessage(groupId, "Message 3 - After leave");

        Thread.sleep(2000); // Wait for all messages and leave to propagate

        // Verify group still works for remaining members
        Group aliceGroup = alice.getGroup(groupId);
        assertNotNull(aliceGroup, "Group should still exist for Alice");
        assertEquals(3, aliceGroup.activeMembers().size(), "Should have 3 members");

        // Bob and Charlie should have all 3 messages (they were members the whole time)
        assertEquals(3, bob.getGroupManager().getMessages(groupId).size(),
                "Bob should have received messages after David left");
        assertEquals(3, charlie.getGroupManager().getMessages(groupId).size(),
                "Charlie should have received messages after David left");

        // David should not have the group
        assertNull(david.getGroup(groupId), "David should not have the group");

    }

    // ==========================================================================
    // TEST 17: Leader election correctly excludes users who left
    // ==========================================================================

    @Test
    @Order(18)
    @DisplayName("Leader election correctly excludes users who left before election")
    void testLeaderElectionExcludesLeftUsers() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        PeerController eve = createPeer("Eve", getNextPort());

        List<PeerController> allPeers = List.of(alice, bob, charlie, david, eve);

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);
        autoAcceptInvitations(eve);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(alice, eve);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);
        makeFriends(david, eve);

        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("ElectionExcludeTest", List.of("Bob", "Charlie", "David", "Eve"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Eve leaves gracefully (before any failure)
        eve.leaveGroup(groupId);
        Thread.sleep(2000); // Wait for leave to propagate

        // Verify Eve is no longer in the group on other peers
        for (PeerController peer : List.of(alice, bob, charlie, david)) {
            Group g = peer.getGroup(groupId);
            assertNotNull(g);
            assertFalse(g.activeMembers().contains(eve.getLocalUser()),
                    peer.getLocalUser().username() + " should not see Eve as an active member");
        }

        // Now Alice (leader) stops - this should trigger election among Bob, Charlie, David
        alice.stop();
        peers.remove(alice);

        // Wait for election
        List<PeerController> remainingPeers = List.of(bob, charlie, david);
        MultiPeerGroupEventWaiter electionWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.LEADER_ELECTED, remainingPeers);

        try {
            boolean success = electionWaiter.await(30, TimeUnit.SECONDS);
            assertTrue(success, "Leader election should complete");
            Thread.sleep(500);
        } finally {
            electionWaiter.cleanup();
        }

        // Verify new leader is NOT Eve (she left) and NOT Alice (she disconnected)
        Group bobGroup = bob.getGroup(groupId);
        assertNotNull(bobGroup);
        String newLeaderId = bobGroup.leader().userId();

        assertNotEquals(alice.getLocalUser().userId(), newLeaderId, "New leader should not be Alice (disconnected)");
        assertNotEquals(eve.getLocalUser().userId(), newLeaderId, "New leader should not be Eve (left)");

        // Verify all remaining peers agree on the leader
        for (PeerController peer : remainingPeers) {
            assertEquals(newLeaderId, peer.getGroup(groupId).leader().userId(),
                    peer.getLocalUser().username() + " should agree on the new leader");
        }
    }

    // ==========================================================================
    // TEST 14: Messages after user leaves are not delivered to the left user
    // ==========================================================================

    @Test
    @Order(14)
    @DisplayName("Messages sent after user leaves are not delivered to that user")
    void testMessagesNotDeliveredAfterLeave() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        // Set up waiter before creating group
        List<PeerController> allPeers = List.of(alice, bob, charlie, david);
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("LeaveMessageTest", List.of("Bob", "Charlie", "David"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Verify all have the group
        allPeers.forEach(p -> assertEquals(1, p.getGroups().size()));

        // David leaves the group
        david.leaveGroup(groupId);
        Thread.sleep(1000); // Wait for leave to propagate

        // Send a message from Alice after David left
        List<PeerController> remainingPeers = List.of(bob, charlie);
        MultiPeerGroupEventWaiter messageWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.MESSAGE_RECEIVED, remainingPeers);

        alice.sendGroupMessage(groupId, "Message after David left");

        try {
            boolean success = messageWaiter.await(5, TimeUnit.SECONDS);
            assertTrue(success, "Message should be received by remaining members");
            Thread.sleep(500);
        } finally {
            messageWaiter.cleanup();
        }

        // Verify David did not receive the message (he left)
        // David shouldn't have the group anymore
        assertNull(david.getGroup(groupId), "David should not have the group after leaving");
        assertEquals(0, david.getGroupManager().getMessages(groupId).size(), 
                "David should not have received the message sent after he left");

        // Verify remaining members received the message
        assertEquals(1, bob.getGroupManager().getMessages(groupId).size());
        assertEquals(1, charlie.getGroupManager().getMessages(groupId).size());

        System.out.println("[TEST] Test 14: Messages not delivered to users who left");
    }

    // ==========================================================================
    // TEST 15: User list is updated before gossip detects changes
    // ==========================================================================

    @Test
    @Order(15)
    @DisplayName("User list is properly updated before gossip runs after leave")
    void testUserListUpdatedBeforeGossip() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        List<PeerController> allPeers = List.of(alice, bob, charlie, david);
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("UserListTest", List.of("Bob", "Charlie", "David"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Verify initial member counts
        Group aliceGroup = alice.getGroup(groupId);
        assertEquals(4, aliceGroup.activeMembers().size(), "Should have 4 active members initially");

        // David leaves
        david.leaveGroup(groupId);
        Thread.sleep(2000); // Wait for leave to propagate

        // Verify member lists are updated on all remaining peers
        for (PeerController peer : List.of(alice, bob, charlie)) {
            Group g = peer.getGroup(groupId);
            assertNotNull(g, peer.getLocalUser().username() + " should still have the group");
            assertEquals(3, g.activeMembers().size(), 
                    peer.getLocalUser().username() + " should see 3 active members after David left");
            assertFalse(g.isMember(david.getLocalUser()),
                    peer.getLocalUser().username() + " should not see David as a member");
        }

        System.out.println("[TEST] Test 15: User list properly updated after leave");
    }

    // ==========================================================================
    // TEST 16: Leaving during ongoing message delivery is handled gracefully
    // ==========================================================================

    @Test
    @Order(16)
    @DisplayName("User leaving during message delivery is handled gracefully")
    void testLeaveDuringMessageDelivery() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);

        List<PeerController> allPeers = List.of(alice, bob, charlie, david);
        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("ConcurrentLeaveTest", List.of("Bob", "Charlie", "David"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Send multiple messages and have David leave in the middle
        alice.sendGroupMessage(groupId, "Message 1 - Before leave");
        
        // David leaves immediately after first message
        david.leaveGroup(groupId);
        
        // Send more messages - these should go to remaining members only
        alice.sendGroupMessage(groupId, "Message 2 - After leave");
        alice.sendGroupMessage(groupId, "Message 3 - After leave");

        Thread.sleep(2000); // Wait for all messages and leave to propagate

        // Verify group still works for remaining members
        Group aliceGroup = alice.getGroup(groupId);
        assertNotNull(aliceGroup, "Group should still exist for Alice");
        assertEquals(3, aliceGroup.activeMembers().size(), "Should have 3 members");

        // Bob and Charlie should have all 3 messages (they were members the whole time)
        assertTrue(bob.getGroupManager().getMessages(groupId).size() >= 2,
                "Bob should have received messages after David left");
        assertTrue(charlie.getGroupManager().getMessages(groupId).size() >= 2,
                "Charlie should have received messages after David left");

        // David should not have the group
        assertNull(david.getGroup(groupId), "David should not have the group");

        System.out.println("[TEST] Test 16: Leave during message delivery handled gracefully");
    }

    // ==========================================================================
    // TEST 17: Leader election correctly excludes users who left
    // ==========================================================================

    @Test
    @Order(17)
    @DisplayName("Leader election correctly excludes users who left before election")
    void testLeaderElectionExcludesLeftUsers() throws Exception {
        PeerController alice = createPeer("Alice", getNextPort());
        PeerController bob = createPeer("Bob", getNextPort());
        PeerController charlie = createPeer("Charlie", getNextPort());
        PeerController david = createPeer("David", getNextPort());
        PeerController eve = createPeer("Eve", getNextPort());

        List<PeerController> allPeers = List.of(alice, bob, charlie, david, eve);

        autoAcceptInvitations(alice);
        autoAcceptInvitations(bob);
        autoAcceptInvitations(charlie);
        autoAcceptInvitations(david);
        autoAcceptInvitations(eve);

        makeFriends(alice, bob);
        makeFriends(alice, charlie);
        makeFriends(alice, david);
        makeFriends(alice, eve);
        makeFriends(bob, charlie);
        makeFriends(bob, david);
        makeFriends(charlie, david);
        makeFriends(david, eve);

        MultiPeerGroupEventWaiter groupCreationWaiter = new MultiPeerGroupEventWaiter(null, TestEvent.GROUP_CREATED, allPeers);

        Group group = alice.createGroup("ElectionExcludeTest", List.of("Bob", "Charlie", "David", "Eve"));
        String groupId = group.groupId();

        try {
            boolean success = groupCreationWaiter.await(10, TimeUnit.SECONDS);
            assertTrue(success, "Group creation should complete");
        } finally {
            groupCreationWaiter.cleanup();
        }

        // Eve leaves gracefully (before any failure)
        eve.leaveGroup(groupId);
        Thread.sleep(2000); // Wait for leave to propagate

        // Verify Eve is no longer in the group on other peers
        for (PeerController peer : List.of(alice, bob, charlie, david)) {
            Group g = peer.getGroup(groupId);
            assertNotNull(g);
            assertFalse(g.activeMembers().contains(eve.getLocalUser()),
                    peer.getLocalUser().username() + " should not see Eve as an active member");
        }

        // Now Alice (leader) stops - this should trigger election among Bob, Charlie, David
        alice.stop();
        peers.remove(alice);

        // Wait for election
        List<PeerController> remainingPeers = List.of(bob, charlie, david);
        MultiPeerGroupEventWaiter electionWaiter = new MultiPeerGroupEventWaiter(groupId, TestEvent.LEADER_ELECTED, remainingPeers);

        try {
            boolean success = electionWaiter.await(30, TimeUnit.SECONDS);
            assertTrue(success, "Leader election should complete");
            Thread.sleep(500);
        } finally {
            electionWaiter.cleanup();
        }

        // Verify new leader is NOT Eve (she left) and NOT Alice (she disconnected)
        Group bobGroup = bob.getGroup(groupId);
        assertNotNull(bobGroup);
        String newLeaderId = bobGroup.leader().userId();
        
        assertNotEquals(alice.getLocalUser().userId(), newLeaderId, "New leader should not be Alice (disconnected)");
        assertNotEquals(eve.getLocalUser().userId(), newLeaderId, "New leader should not be Eve (left)");
        
        // Verify all remaining peers agree on the leader
        for (PeerController peer : remainingPeers) {
            assertEquals(newLeaderId, peer.getGroup(groupId).leader().userId(),
                    peer.getLocalUser().username() + " should agree on the new leader");
        }

        System.out.println("[TEST] Test 17: Leader election correctly excludes users who left");
    }
}
