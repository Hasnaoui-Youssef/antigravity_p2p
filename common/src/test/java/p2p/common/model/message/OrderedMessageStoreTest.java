package p2p.common.model.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p2p.common.vectorclock.VectorClock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderedMessageStore.
 */
class OrderedMessageStoreTest {

    private OrderedMessageStore store;

    private static final String SENDER_A = "senderA";
    private static final String SENDER_B = "senderB";

    @BeforeEach
    void setUp() {
        store = new OrderedMessageStore();
    }

    @Test
    @DisplayName("Messages are stored in causal order")
    void testMessagesStoredInCausalOrder() {
        // Create messages with clear causal ordering
        VectorClock clock1 = createClock(SENDER_A, 1);
        VectorClock clock2 = createClock(SENDER_A, 2);
        VectorClock clock3 = new VectorClock();
        clock3.increment(SENDER_A);
        clock3.increment(SENDER_A);
        clock3.increment(SENDER_B);

        ChatMessage m1 = createTestMessage(SENDER_A, "m1", "First", clock1);
        ChatMessage m2 = createTestMessage(SENDER_A, "m2", "Second", clock2);
        ChatMessage m3 = createTestMessage(SENDER_B, "m3", "Third", clock3);

        // Add in wrong order
        store.addMessage(m3);
        store.addMessage(m1);
        store.addMessage(m2);

        List<Message> ordered = store.getOrderedMessages();

        assertEquals(3, ordered.size());
        assertEquals("m1", ordered.get(0).getMessageId(), "m1 should be first");
        assertEquals("m2", ordered.get(1).getMessageId(), "m2 should be second");
        assertEquals("m3", ordered.get(2).getMessageId(), "m3 should be third");
    }

    @Test
    @DisplayName("getMessagesSince returns correct subset")
    void testGetMessagesSinceReturnsCorrectSubset() {
        VectorClock clock1 = createClock(SENDER_A, 1);
        VectorClock clock2 = createClock(SENDER_A, 2);
        VectorClock clock3 = createClock(SENDER_A, 3);
        VectorClock clock4 = createClock(SENDER_A, 4);

        ChatMessage m1 = createTestMessage(SENDER_A, "m1", "First", clock1);
        ChatMessage m2 = createTestMessage(SENDER_A, "m2", "Second", clock2);
        ChatMessage m3 = createTestMessage(SENDER_A, "m3", "Third", clock3);
        ChatMessage m4 = createTestMessage(SENDER_A, "m4", "Fourth", clock4);

        store.addMessage(m1);
        store.addMessage(m2);
        store.addMessage(m3);
        store.addMessage(m4);

        // Get messages since clock2
        List<Message> since = store.getMessagesSince(clock2);

        // Should return m3 and m4 (messages that happened after clock2)
        assertEquals(2, since.size(), "Should return messages after clock2");
        assertEquals("m3", since.get(0).getMessageId());
        assertEquals("m4", since.get(1).getMessageId());
    }

    @Test
    @DisplayName("Concurrent messages are ordered deterministically")
    void testConcurrentMessagesOrderedDeterministically() {
        // Create concurrent messages
        VectorClock clockA = createClock(SENDER_A, 1);
        VectorClock clockB = createClock(SENDER_B, 1);

        ChatMessage msgA = createTestMessage(SENDER_A, "msgA", "From A", clockA);
        ChatMessage msgB = createTestMessage(SENDER_B, "msgB", "From B", clockB);

        // Add in one order
        store.addMessage(msgB);
        store.addMessage(msgA);

        List<Message> ordered = store.getOrderedMessages();

        // senderA < senderB lexicographically, so msgA should come first
        assertEquals(2, ordered.size());
        assertEquals(SENDER_A, ordered.get(0).getSenderId(), "senderA should come first");
        assertEquals(SENDER_B, ordered.get(1).getSenderId(), "senderB should come second");

        // Create a new store and add in opposite order - should get same result
        OrderedMessageStore store2 = new OrderedMessageStore();
        store2.addMessage(msgA);
        store2.addMessage(msgB);

        List<Message> ordered2 = store2.getOrderedMessages();
        assertEquals(SENDER_A, ordered2.get(0).getSenderId(), "senderA should still come first");
        assertEquals(SENDER_B, ordered2.get(1).getSenderId(), "senderB should still come second");
    }

    @Test
    @DisplayName("Empty store returns empty list")
    void testEmptyStoreReturnsEmptyList() {
        List<Message> messages = store.getOrderedMessages();
        assertTrue(messages.isEmpty(), "Empty store should return empty list");
    }

    @Test
    @DisplayName("getMessagesSince with null clock returns all messages")
    void testGetMessagesSinceNullClockReturnsAll() {
        VectorClock clock1 = createClock(SENDER_A, 1);
        VectorClock clock2 = createClock(SENDER_A, 2);

        ChatMessage m1 = createTestMessage(SENDER_A, "m1", "First", clock1);
        ChatMessage m2 = createTestMessage(SENDER_A, "m2", "Second", clock2);

        store.addMessage(m1);
        store.addMessage(m2);

        List<Message> since = store.getMessagesSince(null);

        assertEquals(2, since.size(), "Should return all messages when since is null");
    }

    @Test
    @DisplayName("getMessagesSince with empty clock returns all messages")
    void testGetMessagesSinceEmptyClockReturnsAll() {
        VectorClock clock1 = createClock(SENDER_A, 1);
        VectorClock clock2 = createClock(SENDER_A, 2);

        ChatMessage m1 = createTestMessage(SENDER_A, "m1", "First", clock1);
        ChatMessage m2 = createTestMessage(SENDER_A, "m2", "Second", clock2);

        store.addMessage(m1);
        store.addMessage(m2);

        List<Message> since = store.getMessagesSince(new VectorClock());

        assertEquals(2, since.size(), "Should return all messages when since is empty clock");
    }

    @Test
    @DisplayName("Duplicate messages are not stored twice")
    void testDuplicateMessagesNotStored() {
        VectorClock clock = createClock(SENDER_A, 1);
        ChatMessage msg = createTestMessage(SENDER_A, "msg1", "Content", clock);

        store.addMessage(msg);
        store.addMessage(msg); // Add same message again

        List<Message> messages = store.getOrderedMessages();
        assertEquals(1, messages.size(), "Duplicate message should not be stored");
    }

    @Test
    @DisplayName("getMessagesSince excludes messages at the since clock")
    void testGetMessagesSinceExcludesAtClock() {
        VectorClock clock1 = createClock(SENDER_A, 1);
        VectorClock clock2 = createClock(SENDER_A, 2);

        ChatMessage m1 = createTestMessage(SENDER_A, "m1", "First", clock1);
        ChatMessage m2 = createTestMessage(SENDER_A, "m2", "Second", clock2);

        store.addMessage(m1);
        store.addMessage(m2);

        // Get messages since the clock of m1
        List<Message> since = store.getMessagesSince(clock1);

        assertEquals(1, since.size(), "Should return only messages after clock1");
        assertEquals("m2", since.get(0).getMessageId(), "Should return m2");
    }

    // Helper methods

    private VectorClock createClock(String processId, int time) {
        VectorClock clock = new VectorClock();
        for (int i = 0; i < time; i++) {
            clock.increment(processId);
        }
        return clock;
    }

    private ChatMessage createTestMessage(String senderId, String messageId, String content, VectorClock clock) {
        return new ChatMessage(
                messageId,
                senderId,
                "User-" + senderId,
                "receiver",
                content,
                System.currentTimeMillis(),
                ChatMessage.ChatSubtopic.DIRECT,
                clock);
    }
}
