package p2p.peer.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p2p.common.model.User;
import p2p.common.model.message.ChatMessage;
import p2p.common.model.message.Message;
import p2p.common.vectorclock.VectorClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CausalDeliveryManager.
 * Tests are written FIRST following TDD approach.
 */
class CausalDeliveryManagerTest {

    private CausalDeliveryManager manager;
    private static final String PROCESS_A = "processA";
    private static final String PROCESS_B = "processB";

    @BeforeEach
    void setUp() {
        VectorClock localClock = new VectorClock();
        manager = new CausalDeliveryManager(localClock);
    }

    @Test
    @DisplayName("Message is deliverable when it's the first from a sender")
    void testFirstMessageFromSenderIsDeliverable() {
        // Create a message with clock {processA: 1}
        VectorClock msgClock = new VectorClock();
        msgClock.increment(PROCESS_A);

        ChatMessage message = createTestMessage(PROCESS_A, "Hello", msgClock);

        assertTrue(manager.isDeliverable(message),
                "First message from a sender should be deliverable");
    }

    @Test
    @DisplayName("Message is buffered when its causal dependencies are not met")
    void testMessageBufferedWhenDependenciesNotMet() {
        // Create a message with clock {processA: 2} - but we haven't seen {processA: 1}
        VectorClock msgClock = new VectorClock();
        msgClock.increment(PROCESS_A);
        msgClock.increment(PROCESS_A); // Now at 2

        ChatMessage message = createTestMessage(PROCESS_A, "Out of order", msgClock);

        assertFalse(manager.isDeliverable(message),
                "Message should not be deliverable when previous message from sender is missing");
    }

    @Test
    @DisplayName("Buffered messages are delivered when dependencies become available")
    void testBufferedMessagesDeliveredWhenDependenciesMet() {
        List<Message> delivered = new ArrayList<>();

        // Create messages with clocks {processA: 1} and {processA: 2}
        VectorClock clock1 = new VectorClock();
        clock1.increment(PROCESS_A); // {processA: 1}

        VectorClock clock2 = new VectorClock();
        clock2.increment(PROCESS_A);
        clock2.increment(PROCESS_A); // {processA: 2}

        ChatMessage msg1 = createTestMessage(PROCESS_A, "First", clock1);
        ChatMessage msg2 = createTestMessage(PROCESS_A, "Second", clock2);

        // First, try to deliver msg2 (should be buffered)
        manager.bufferOrDeliver(msg2, delivered::add);
        assertEquals(0, delivered.size(), "Message 2 should be buffered");
        assertEquals(1, manager.getPendingCount(), "One message should be pending");

        // Now deliver msg1 - this should trigger delivery of both
        manager.bufferOrDeliver(msg1, delivered::add);
        assertEquals(2, delivered.size(), "Both messages should now be delivered");
        assertEquals(0, manager.getPendingCount(), "No messages should be pending");

        // Verify order
        assertEquals("First", ((ChatMessage) delivered.get(0)).getContent());
        assertEquals("Second", ((ChatMessage) delivered.get(1)).getContent());
    }

    @Test
    @DisplayName("Concurrent messages are handled correctly")
    void testConcurrentMessagesHandled() {
        List<Message> delivered = new ArrayList<>();

        // Two concurrent messages from different processes
        VectorClock clockA = new VectorClock();
        clockA.increment(PROCESS_A); // {processA: 1}

        VectorClock clockB = new VectorClock();
        clockB.increment(PROCESS_B); // {processB: 1}

        ChatMessage msgA = createTestMessage(PROCESS_A, "From A", clockA);
        ChatMessage msgB = createTestMessage(PROCESS_B, "From B", clockB);

        // Both should be deliverable since they're concurrent
        manager.bufferOrDeliver(msgA, delivered::add);
        manager.bufferOrDeliver(msgB, delivered::add);

        assertEquals(2, delivered.size(), "Both concurrent messages should be delivered");
    }

    @Test
    @DisplayName("Messages from multiple senders with interleaved delivery")
    void testMultipleSendersInterleaved() {
        List<Message> delivered = new ArrayList<>();

        // Process A sends 3 messages
        VectorClock clockA1 = createClock(PROCESS_A, 1);
        VectorClock clockA2 = createClock(PROCESS_A, 2);
        VectorClock clockA3 = createClock(PROCESS_A, 3);

        // Process B sends 2 messages
        VectorClock clockB1 = createClock(PROCESS_B, 1);
        VectorClock clockB2 = createClock(PROCESS_B, 2);

        ChatMessage msgA1 = createTestMessage(PROCESS_A, "A1", clockA1);
        ChatMessage msgA2 = createTestMessage(PROCESS_A, "A2", clockA2);
        ChatMessage msgA3 = createTestMessage(PROCESS_A, "A3", clockA3);
        ChatMessage msgB1 = createTestMessage(PROCESS_B, "B1", clockB1);
        ChatMessage msgB2 = createTestMessage(PROCESS_B, "B2", clockB2);

        // Deliver in order: A3 (buffer), B2 (buffer), A1 (deliver), B1 (deliver +
        // triggers B2)
        manager.bufferOrDeliver(msgA3, delivered::add);
        assertEquals(0, delivered.size(), "A3 should be buffered");

        manager.bufferOrDeliver(msgB2, delivered::add);
        assertEquals(0, delivered.size(), "B2 should be buffered");

        manager.bufferOrDeliver(msgA1, delivered::add);
        assertEquals(1, delivered.size(), "A1 should be delivered");

        manager.bufferOrDeliver(msgB1, delivered::add);
        // B1 is delivered, which triggers B2 since B2's deps are now satisfied
        assertEquals(3, delivered.size(), "B1 should be delivered and trigger B2");

        // Deliver A2 - should trigger A2 and A3
        manager.bufferOrDeliver(msgA2, delivered::add);
        assertEquals(5, delivered.size(), "A2 should trigger A3, all messages delivered");
    }

    @Test
    @DisplayName("Message with unknown causal dependency is buffered")
    void testMessageWithUnknownDependencyBuffered() {
        List<Message> delivered = new ArrayList<>();

        // Create a message that depends on a message from another process
        // Clock: {processA: 1, processB: 1} - depends on seeing {processB: 1}
        VectorClock msgClock = new VectorClock();
        msgClock.increment(PROCESS_A);
        msgClock.increment(PROCESS_B);

        ChatMessage message = createTestMessage(PROCESS_A, "Depends on B", msgClock);

        manager.bufferOrDeliver(message, delivered::add);
        assertEquals(0, delivered.size(), "Message should be buffered due to unknown dependency");
        assertEquals(1, manager.getPendingCount(), "One message should be pending");
    }

    @Test
    @DisplayName("getPendingMessages returns correct list")
    void testGetPendingMessages() {
        // Buffer some messages
        VectorClock clock2 = createClock(PROCESS_A, 2);
        VectorClock clock3 = createClock(PROCESS_A, 3);

        ChatMessage msg2 = createTestMessage(PROCESS_A, "Second", clock2);
        ChatMessage msg3 = createTestMessage(PROCESS_A, "Third", clock3);

        manager.bufferOrDeliver(msg2, m -> {
        });
        manager.bufferOrDeliver(msg3, m -> {
        });

        List<Message> pending = manager.getPendingMessages();
        assertEquals(2, pending.size(), "Two messages should be pending");
    }

    @Test
    @DisplayName("isDeliverable correctly checks causal dependencies for all processes")
    void testIsDeliverableChecksAllProcesses() {
        // First, deliver a message from process B
        VectorClock clockB1 = createClock(PROCESS_B, 1);
        ChatMessage msgB1 = createTestMessage(PROCESS_B, "B1", clockB1);
        manager.bufferOrDeliver(msgB1, m -> {
        });

        // Now try a message from A that has seen B's message
        // Clock: {processA: 1, processB: 1}
        VectorClock clockA1 = new VectorClock();
        clockA1.increment(PROCESS_A);
        clockA1.update(clockB1); // A has seen B's message

        ChatMessage msgA1 = createTestMessage(PROCESS_A, "A1", clockA1);
        assertTrue(manager.isDeliverable(msgA1),
                "Message should be deliverable since we've seen the dependency");
    }

    @Test
    @DisplayName("Delivering a message updates the local clock")
    void testDeliveryUpdatesLocalClock() {
        VectorClock clock1 = createClock(PROCESS_A, 1);
        ChatMessage msg1 = createTestMessage(PROCESS_A, "First", clock1);

        AtomicInteger deliveryCount = new AtomicInteger(0);
        manager.bufferOrDeliver(msg1, m -> deliveryCount.incrementAndGet());

        assertEquals(1, deliveryCount.get(), "Message should be delivered");

        // Now a second message should be deliverable
        VectorClock clock2 = createClock(PROCESS_A, 2);
        ChatMessage msg2 = createTestMessage(PROCESS_A, "Second", clock2);

        assertTrue(manager.isDeliverable(msg2),
                "Second message should be deliverable after first was delivered");
    }

    // Helper methods

    private VectorClock createClock(String processId, int time) {
        VectorClock clock = new VectorClock();
        for (int i = 0; i < time; i++) {
            clock.increment(processId);
        }
        return clock;
    }

    private ChatMessage createTestMessage(String senderId, String content, VectorClock clock) {
        User sender = new User(senderId, "User-" + senderId, "127.0.0.1", 8080);
        return ChatMessage.createDirect(sender, "receiver", content, clock);
    }
}
