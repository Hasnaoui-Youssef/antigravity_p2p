package p2p.common.model.message;

import p2p.common.vectorclock.VectorClock;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * A data structure that stores messages ordered by their vector clocks
 * with deterministic ordering for concurrent events.
 * 
 * Ordering rules:
 * 1. If m1.clock.happensBefore(m2.clock) -> m1 comes before m2
 * 2. If m2.clock.happensBefore(m1.clock) -> m2 comes before m1
 * 3. If concurrent (neither happens-before): compare sender IDs
 * lexicographically
 * 4. If same sender: compare message IDs lexicographically
 */
public class OrderedMessageStore {

    private final Set<Message> messages;
    private final CausalOrderComparator comparator;

    public OrderedMessageStore() {
        this.comparator = new CausalOrderComparator();
        this.messages = new ConcurrentSkipListSet<>(comparator);
    }

    /**
     * Adds a message to the store.
     * Duplicate messages (same message ID) are ignored.
     * 
     * @param message The message to add
     */
    public void addMessage(Message message) {
        if (message != null) {
            messages.add(message);
        }
    }

    /**
     * Gets all messages in causal order.
     * 
     * @return A list of all messages ordered by their vector clocks
     */
    public List<Message> getOrderedMessages() {
        return new ArrayList<>(messages);
    }

    /**
     * Gets messages that happened after the given clock.
     * 
     * @param since The vector clock to compare against. Messages with clocks
     *              that happen after this clock are returned.
     *              If null or empty, all messages are returned.
     * @return A list of messages that happened after the given clock
     */
    public List<Message> getMessagesSince(VectorClock since) {
        if (since == null) {
            return getOrderedMessages();
        }

        Map<String, Integer> sinceSnapshot = since.getClockSnapshot();
        if (sinceSnapshot.isEmpty()) {
            return getOrderedMessages();
        }

        List<Message> result = new ArrayList<>();

        for (Message message : messages) {
            VectorClock msgClock = message.getVectorClock();
            if (msgClock == null) {
                continue;
            }

            // A message happened "after" the since clock if:
            // since.happensBefore(msgClock) is true
            // This means the message clock is causally after the since clock
            if (since.happensBefore(msgClock)) {
                result.add(message);
            }
        }

        return result;
    }

    /**
     * Gets the number of messages in the store.
     * 
     * @return The number of messages
     */
    public int size() {
        return messages.size();
    }

    /**
     * Checks if the store is empty.
     * 
     * @return true if there are no messages
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * Clears all messages from the store.
     */
    public void clear() {
        messages.clear();
    }
}
