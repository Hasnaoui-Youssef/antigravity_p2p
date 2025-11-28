package p2p.common.model;

import p2p.common.vectorclock.VectorClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import p2p.common.model.message.DirectMessage;
import p2p.common.model.message.Message;
import p2p.common.model.MessageTopic;
import p2p.common.model.User;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DirectMessage model.
 */
class MessageTest {

    private User sender;
    private VectorClock clock;

    @BeforeEach
    void setUp() {
        sender = User.create("Alice", "192.168.1.1", 5001);
        clock = new VectorClock();
        clock.increment(sender.getUserId());
    }

    @Test
    @DisplayName("Create should generate unique message ID")
    void testCreate() {
        DirectMessage msg1 = DirectMessage.create(sender, "receiver-id", "Hello", clock);
        DirectMessage msg2 = DirectMessage.create(sender, "receiver-id", "Hi", clock);

        assertNotNull(msg1.getMessageId());
        assertNotNull(msg2.getMessageId());
        assertNotEquals(msg1.getMessageId(), msg2.getMessageId());
    }

    @Test
    @DisplayName("Create should set sender information correctly")
    void testCreateSenderInfo() {
        DirectMessage msg = DirectMessage.create(sender, "receiver-id", "Test message", clock);

        assertEquals(sender.getUserId(), msg.getSenderId());
        assertEquals(sender.getUsername(), msg.getSenderUsername());
        assertEquals("receiver-id", msg.getReceiverId());
        assertEquals("Test message", msg.getContent());
    }

    @Test
    @DisplayName("VectorClock should be cloned independently")
    void testVectorClockIndependence() {
        DirectMessage msg = DirectMessage.create(sender, "receiver-id", "Test", clock);

        VectorClock msgClock = msg.getVectorClock();
        clock.increment(sender.getUserId());

        // Original clock changed, message clock should not
        assertEquals(2, clock.getTime(sender.getUserId()));
        assertEquals(1, msgClock.getTime(sender.getUserId()));
    }

    @Test
    @DisplayName("Timestamp should be set to current time")
    void testTimestamp() {
        long before = System.currentTimeMillis();
        DirectMessage msg = DirectMessage.create(sender, "receiver-id", "Test", clock);
        long after = System.currentTimeMillis();

        assertTrue(msg.getTimestamp() >= before);
        assertTrue(msg.getTimestamp() <= after);
    }

    @Test
    @DisplayName("Equals should compare by message ID")
    void testEquals() {
        DirectMessage msg1 = new DirectMessage("msg123", sender.getUserId(), sender.getUsername(),
                "receiver", "content1", 1000, clock);
        DirectMessage msg2 = new DirectMessage("msg123", sender.getUserId(), sender.getUsername(),
                "receiver", "content2", 2000, clock); // Different content but same ID
        DirectMessage msg3 = new DirectMessage("msg456", sender.getUserId(), sender.getUsername(),
                "receiver", "content1", 1000, clock);

        assertEquals(msg1, msg2);
        assertNotEquals(msg1, msg3);
    }

    @Test
    @DisplayName("ToString should include sender and content")
    void testToString() {
        DirectMessage msg = DirectMessage.create(sender, "receiver-id", "Hello!", clock);
        String str = msg.toString();

        assertTrue(str.contains(sender.getUsername()));
        assertTrue(str.contains("Hello!"));
    }
}
