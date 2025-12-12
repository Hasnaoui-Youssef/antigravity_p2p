package p2p.common.model.message;

import p2p.common.vectorclock.VectorClock;

import java.util.Comparator;

/**
 * Comparator for ordering messages by their vector clocks with deterministic
 * ordering for concurrent events.
 *
 * Ordering rules:
 * 1. If m1.clock.happensBefore(m2.clock) -> m1 comes before m2
 * 2. If m2.clock.happensBefore(m1.clock) -> m2 comes before m1
 * 3. If concurrent (neither happens-before): compare sender IDs
 * lexicographically
 * 4. If same sender with concurrent clocks: throw IllegalStateException (this
 * is a bug)
 */
public class CausalOrderComparator implements Comparator<Message> {

    @Override
    public int compare(Message m1, Message m2) {
        if (m1 == m2) {
            return 0;
        }

        VectorClock clock1 = m1.getVectorClock();
        VectorClock clock2 = m2.getVectorClock();

        if (clock1 == null || clock2 == null) {
            throw new IllegalArgumentException("Clocks cannot be null");
        }

        if (clock1.happensBefore(clock2)) {
            return -1;
        }
        if (clock2.happensBefore(clock1)) {
            return 1;
        }

        // Concurrent events or no clocks: use sender ID as primary tiebreaker
        int senderCompare = m1.getSenderId().compareTo(m2.getSenderId());
        if (senderCompare != 0) {
            return senderCompare;
        }

        // Same sender with concurrent messages indicates a failure - messages from
        // the same sender should always have a happens-before relationship
        throw new IllegalStateException(
                "Messages from the same sender should never be concurrent. " +
                        "Sender: " + m1.getSenderId() + ", Messages: " + m1.getMessageId() + ", " + m2.getMessageId());
    }
}
