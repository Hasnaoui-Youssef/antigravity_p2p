package p2p.common.model.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CausalOrderComparator.
 */
class CausalOrderComparatorTest {

    private CausalOrderComparator comparator;

    private static final String SENDER_A = "senderA";
    private static final String SENDER_B = "senderB";
    private static final String SENDER_C = "senderC";

    @BeforeEach
    void setUp() {
        comparator = new CausalOrderComparator();
    }

    @Test
    @DisplayName("Message with earlier clock comes first (happens-before)")
    void testHappensBeforeOrdering() {
        // Create two messages where m1 happens before m2
        VectorClock clock1 = createClock(SENDER_A, 1);
        VectorClock clock2 = createClock(SENDER_A, 2);
        clock2.increment(SENDER_B); // m2 has more knowledge

        ChatMessage m1 = createTestMessage(SENDER_A, "msg1", "First", clock1);
        ChatMessage m2 = createTestMessage(SENDER_A, "msg2", "Second", clock2);

        assertTrue(comparator.compare(m1, m2) < 0,
                "m1 should come before m2 (happens-before)");
        assertTrue(comparator.compare(m2, m1) > 0,
                "m2 should come after m1");
    }

    @Test
    @DisplayName("Concurrent events use sender ID as tiebreaker")
    void testConcurrentEventsSenderIdTiebreaker() {
        // Create two concurrent messages from different senders
        VectorClock clockA = createClock(SENDER_A, 1);
        VectorClock clockB = createClock(SENDER_B, 1);

        ChatMessage msgA = createTestMessage(SENDER_A, "msgA", "From A", clockA);
        ChatMessage msgB = createTestMessage(SENDER_B, "msgB", "From B", clockB);

        // senderA < senderB lexicographically
        assertTrue(comparator.compare(msgA, msgB) < 0,
                "Message from senderA should come before senderB (lexicographic)");
        assertTrue(comparator.compare(msgB, msgA) > 0,
                "Message from senderB should come after senderA");
    }

    @Test
    @DisplayName("Same sender with concurrent clocks throws exception")
    void testSameSenderConcurrentClocksThrowsException() {
        VectorClock clock = createClock(SENDER_A, 1);

        // Same sender, same clock - this is invalid and should throw
        ChatMessage msg1 = createTestMessage(SENDER_A, "aaa-msg", "First", clock);
        ChatMessage msg2 = createTestMessage(SENDER_A, "bbb-msg", "Second", clock);

        // Messages from the same sender should always have a happens-before
        // relationship
        assertThrows(IllegalStateException.class, () -> comparator.compare(msg1, msg2),
                "Concurrent messages from the same sender should throw IllegalStateException");
    }

    @Test
    @DisplayName("Sorting a list of messages produces causal order")
    void testSortingProducesCausalOrder() {
        // Create messages in arbitrary order
        VectorClock clock1 = createClock(SENDER_A, 1);
        VectorClock clock2 = createClock(SENDER_A, 2);
        VectorClock clock3 = createClock(SENDER_B, 1); // Concurrent with clock1
        VectorClock clock4 = new VectorClock();
        clock4.increment(SENDER_A);
        clock4.increment(SENDER_A);
        clock4.increment(SENDER_B); // {A:2, B:1} - happens after clock1, clock2, clock3

        ChatMessage m1 = createTestMessage(SENDER_A, "m1", "First from A", clock1);
        ChatMessage m2 = createTestMessage(SENDER_A, "m2", "Second from A", clock2);
        ChatMessage m3 = createTestMessage(SENDER_B, "m3", "First from B", clock3);
        ChatMessage m4 = createTestMessage(SENDER_A, "m4", "Combined knowledge", clock4);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(m4); // Add in wrong order
        messages.add(m2);
        messages.add(m3);
        messages.add(m1);

        Collections.sort(messages, comparator);

        // m1 should be first (clock {A:1})
        // m2 should come after m1 (clock {A:2})
        // m3 is concurrent with m1, but senderA < senderB, so m1 comes first
        // m4 should be last (happens after all others)

        assertEquals("m1", messages.get(0).getMessageId(), "m1 should be first");
        // m2 and m3 are both after m1, but m3 is concurrent so ordering depends on
        // tiebreaker
        assertEquals("m4", messages.get(3).getMessageId(), "m4 should be last");
    }

    @Test
    @DisplayName("Equal messages compare as equal")
    void testEqualMessagesCompareEqual() {
        VectorClock clock = createClock(SENDER_A, 1);
        ChatMessage msg = createTestMessage(SENDER_A, "same-id", "Content", clock);

        assertEquals(0, comparator.compare(msg, msg),
                "Same message should compare as equal");
    }

    @Test
    @DisplayName("Messages with null clocks are compared by sender ID")
    void testNullClockFallback() {
        // This tests edge case handling - empty/null clocks use sender ID comparison
        ChatMessage msg1 = createTestMessageNullClock(SENDER_A, "msg1", "First");
        ChatMessage msg2 = createTestMessageNullClock(SENDER_B, "msg2", "Second");

        // Should fall back to comparing sender IDs (senderA < senderB)
        assertTrue(comparator.compare(msg1, msg2) < 0,
                "Without clocks, should use sender ID as tiebreaker");
    }

    @Test
    @DisplayName("Messages from same sender with null clocks are not comparable")
    void testNullClockSameSenderFallback() {
        // Null/empty clocks from same sender fall back to message ID
        ChatMessage msg1 = createTestMessageNullClock(SENDER_A, "aaa-msg", "First");
        ChatMessage msg2 = createTestMessageNullClock(SENDER_A, "bbb-msg", "Second");

        // Should fall back to comparing message IDs
        assertThrows(IllegalStateException.class, () -> comparator.compare(msg1, msg2),
                "Messages without clocks should not be comparable");
    }

    @Test
    @DisplayName("Three concurrent messages are ordered by sender ID")
    void testThreeConcurrentMessagesSenderOrder() {
        VectorClock clockA = createClock(SENDER_A, 1);
        VectorClock clockB = createClock(SENDER_B, 1);
        VectorClock clockC = createClock(SENDER_C, 1);

        ChatMessage msgA = createTestMessage(SENDER_A, "msgA", "From A", clockA);
        ChatMessage msgB = createTestMessage(SENDER_B, "msgB", "From B", clockB);
        ChatMessage msgC = createTestMessage(SENDER_C, "msgC", "From C", clockC);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(msgC);
        messages.add(msgA);
        messages.add(msgB);

        Collections.sort(messages, comparator);

        // Lexicographic order: senderA < senderB < senderC
        assertEquals(SENDER_A, messages.get(0).getSenderId());
        assertEquals(SENDER_B, messages.get(1).getSenderId());
        assertEquals(SENDER_C, messages.get(2).getSenderId());
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
        User sender = new User(senderId, "User-" + senderId, "127.0.0.1", 8080);
        // We need to create a message with a specific ID
        return new ChatMessage(
                messageId,
                senderId,
                sender.username(),
                "receiver",
                content,
                System.currentTimeMillis(),
                ChatMessage.ChatSubtopic.DIRECT,
                clock);
    }

    private ChatMessage createTestMessageNullClock(String senderId, String messageId, String content) {
        // Create a message without a vector clock for edge case testing
        return new ChatMessage(
                messageId,
                senderId,
                "User-" + senderId,
                "receiver",
                content,
                System.currentTimeMillis(),
                ChatMessage.ChatSubtopic.DIRECT,
                new VectorClock() // Empty clock
        );
    }
}
