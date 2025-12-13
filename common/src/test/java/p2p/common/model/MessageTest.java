package p2p.common.model;

import p2p.common.vectorclock.VectorClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import p2p.common.model.message.ChatMessage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChatMessage model.
 */
class MessageTest {

    private User sender;
    private VectorClock clock;

    @BeforeEach
    void setUp() {
        sender = User.create("Alice", "192.168.1.1", 5001);
        clock = new VectorClock();
        clock.increment(sender.userId());
    }

    @Test
    @DisplayName("Create should generate unique message ID")
    void testCreate() {
        ChatMessage msg1 = ChatMessage.createDirect(sender, "receiver-id", "Hello", clock);
        ChatMessage msg2 = ChatMessage.createDirect(sender, "receiver-id", "Hi", clock);

        assertNotNull(msg1.getMessageId());
        assertNotNull(msg2.getMessageId());
        assertNotEquals(msg1.getMessageId(), msg2.getMessageId());
    }

    @Test
    @DisplayName("Create should set sender information correctly")
    void testCreateSenderInfo() {
        ChatMessage msg = ChatMessage.createDirect(sender, "receiver-id", "Test message", clock);

        assertEquals(sender.userId(), msg.getSenderId());
        assertEquals(sender.username(), msg.getSenderUsername());
        assertEquals("receiver-id", msg.getTargetId());
        assertEquals("Test message", msg.getContent());
        assertEquals(ChatMessage.ChatSubtopic.DIRECT, msg.getSubtopic());
    }

    @Test
    @DisplayName("VectorClock should be cloned independently")
    void testVectorClockIndependence() {
        ChatMessage msg = ChatMessage.createDirect(sender, "receiver-id", "Test", clock);

        VectorClock msgClock = msg.getVectorClock();
        clock.increment(sender.userId());

        // Original clock changed, message clock should not
        assertEquals(2, clock.getTime(sender.userId()));
        assert msgClock != null;
        assertEquals(1, msgClock.getTime(sender.userId()));
    }

    @Test
    @DisplayName("Timestamp should be set to current time")
    void testTimestamp() {
        long before = System.currentTimeMillis();
        ChatMessage msg = ChatMessage.createDirect(sender, "receiver-id", "Test", clock);
        long after = System.currentTimeMillis();

        assertTrue(msg.getTimestamp() >= before);
        assertTrue(msg.getTimestamp() <= after);
    }

    @Test
    @DisplayName("Equals should compare by message ID")
    void testEquals() {
        ChatMessage msg1 = new ChatMessage("msg123", sender.userId(), sender.username(),
                "receiver", "content1", 1000, ChatMessage.ChatSubtopic.DIRECT, clock);
        ChatMessage msg2 = new ChatMessage("msg123", sender.userId(), sender.username(),
                "receiver", "content2", 2000, ChatMessage.ChatSubtopic.DIRECT, clock); // Different content but same ID
        ChatMessage msg3 = new ChatMessage("msg456", sender.userId(), sender.username(),
                "receiver", "content1", 1000, ChatMessage.ChatSubtopic.DIRECT, clock);

        assertEquals(msg1, msg2);
        assertNotEquals(msg1, msg3);
    }

    @Test
    @DisplayName("ToString should include sender and content length")
    void testToString() {
        ChatMessage msg = ChatMessage.createDirect(sender, "receiver-id", "Hello!", clock);
        String str = msg.toString();

        assertTrue(str.contains(sender.username()));
        assertTrue(str.contains("contentLength"));
    }
}
