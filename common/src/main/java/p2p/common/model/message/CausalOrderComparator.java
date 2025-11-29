package p2p.common.model.message;

import p2p.common.vectorclock.VectorClock;

import java.util.Comparator;

/**
 * Comparator for ordering messages by their vector clocks with deterministic
 * ordering for concurrent events.
 * 
 * Ordering rules:
 * 1. If m1.clock.happensBefore(m2.clock) → m1 comes before m2
 * 2. If m2.clock.happensBefore(m1.clock) → m2 comes before m1
 * 3. If concurrent (neither happens-before): compare sender IDs lexicographically
 * 4. If same sender: compare message IDs lexicographically
 */
public class CausalOrderComparator implements Comparator<Message> {

    @Override
    public int compare(Message m1, Message m2) {
        if (m1 == m2) {
            return 0;
        }
        
        VectorClock clock1 = m1.getVectorClock();
        VectorClock clock2 = m2.getVectorClock();
        
        // Handle null clocks by treating them as "empty" clocks
        boolean hasClocks = clock1 != null && clock2 != null;
        
        if (hasClocks) {
            // Check for happens-before relationship
            if (clock1.happensBefore(clock2)) {
                return -1;  // m1 comes before m2
            }
            if (clock2.happensBefore(clock1)) {
                return 1;   // m2 comes before m1
            }
            
            // They are concurrent - use tiebreakers
        }
        
        // Concurrent events or no clocks: use sender ID as primary tiebreaker
        int senderCompare = m1.getSenderId().compareTo(m2.getSenderId());
        if (senderCompare != 0) {
            return senderCompare;
        }
        
        // Same sender: use message ID as final tiebreaker
        return m1.getMessageId().compareTo(m2.getMessageId());
    }
}
