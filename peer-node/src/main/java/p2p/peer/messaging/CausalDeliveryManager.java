package p2p.peer.messaging;

import p2p.common.model.message.Message;
import p2p.common.vectorclock.VectorClock;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Manages causal delivery of messages based on vector clocks.
 * Buffers messages that arrive before their causal dependencies are satisfied
 * and delivers them in causal order.
 * A message is deliverable when:
 * 1. msgClock[sender] == localClock[sender] + 1 (next expected from sender)
 * 2. For all other processes p: msgClock[p] <= localClock[p] (no unknown causal
 * dependencies)
 */
public class CausalDeliveryManager {

    private final VectorClock localClock;
    private final Map<String, Queue<Message>> pendingMessages;

    /**
     * Creates a new CausalDeliveryManager.
     *
     * @param localClock The local vector clock (will be updated on message
     *                   delivery)
     */
    public CausalDeliveryManager(VectorClock localClock) {
        this.localClock = Objects.requireNonNull(localClock);
        this.pendingMessages = new ConcurrentHashMap<>();
    }

    /**
     * Checks if a message is deliverable based on causal ordering.
     * A message is deliverable when:
     * 1. msgClock[sender] == localClock[sender] + 1 (next expected from sender)
     * 2. For all other processes p: msgClock[p] <= localClock[p] (no unknown causal
     * dependencies)
     *
     * @param message The message to check
     * @return true if the message can be delivered, false if it should be buffered
     */
    public boolean isDeliverable(Message message) {
        VectorClock msgClock = message.getVectorClock();
        if (msgClock == null) {
            // Messages without vector clocks are always deliverable
            return true;
        }

        String senderId = message.getSenderId();
        Map<String, Integer> msgClockSnapshot = msgClock.getClockSnapshot();

        synchronized (localClock) {
            // Check condition 1: msgClock[sender] == localClock[sender] + 1
            int msgSenderTime = msgClockSnapshot.getOrDefault(senderId, 0);
            int localSenderTime = localClock.getTime(senderId);

            if (msgSenderTime != localSenderTime + 1) {
                return false;
            }

            // Check condition 2: For all other processes p: msgClock[p] <= localClock[p]
            for (Map.Entry<String, Integer> entry : msgClockSnapshot.entrySet()) {
                String processId = entry.getKey();
                if (processId.equals(senderId)) {
                    continue; // Already checked above
                }

                int msgTime = entry.getValue();
                int localTime = localClock.getTime(processId);

                if (msgTime > localTime) {
                    return false; // Unknown causal dependency
                }
            }
        }

        return true;
    }

    /**
     * Attempts to deliver a message, buffering it if causal dependencies are not
     * met.
     * After delivering a message, checks if any buffered messages become
     * deliverable.
     *
     * @param message          The message to deliver
     * @param deliveryCallback Called for each message that is delivered (in causal
     *                         order)
     */
    public void bufferOrDeliver(Message message, Consumer<Message> deliveryCallback) {
        if (isDeliverable(message)) {
            deliverMessage(message, deliveryCallback);
            // Check if any buffered messages can now be delivered
            deliverPendingMessages(deliveryCallback);
        } else {
            bufferMessage(message);
        }
    }

    /**
     * Delivers a message and updates the local clock.
     */
    private void deliverMessage(Message message, Consumer<Message> callback) {
        VectorClock msgClock = message.getVectorClock();
        if (msgClock != null) {
            synchronized (localClock) {
                localClock.update(msgClock);
            }
        }
        callback.accept(message);
    }

    /**
     * Buffers a message for later delivery.
     */
    private void bufferMessage(Message message) {
        String senderId = message.getSenderId();
        pendingMessages.computeIfAbsent(senderId, k -> new ConcurrentLinkedQueue<>()).add(message);
    }

    /**
     * Attempts to deliver any buffered messages that are now deliverable.
     * Continues until no more messages can be delivered.
     */
    private void deliverPendingMessages(Consumer<Message> deliveryCallback) {
        boolean delivered;
        do {
            delivered = false;

            // Collect all pending messages
            List<Message> allPending = new ArrayList<>();
            for (Queue<Message> queue : pendingMessages.values()) {
                allPending.addAll(queue);
            }

            // Sort by sender time to try delivering in order
            allPending.sort(Comparator.comparing(m -> {
                VectorClock clock = m.getVectorClock();
                return clock != null ? clock.getTime(m.getSenderId()) : 0;
            }));

            // Try to deliver each pending message
            for (Message pending : allPending) {
                if (isDeliverable(pending)) {
                    // Remove from pending queue
                    String senderId = pending.getSenderId();
                    Queue<Message> queue = pendingMessages.get(senderId);
                    if (queue != null) {
                        queue.remove(pending);
                        if (queue.isEmpty()) {
                            pendingMessages.remove(senderId);
                        }
                    }

                    // Deliver the message
                    deliverMessage(pending, deliveryCallback);
                    delivered = true;
                }
            }
        } while (delivered);
    }

    /**
     * Gets the count of pending (buffered) messages.
     *
     * @return The number of messages waiting to be delivered
     */
    public int getPendingCount() {
        return pendingMessages.values().stream()
                .mapToInt(Queue::size)
                .sum();
    }

    /**
     * Gets all pending (buffered) messages.
     *
     * @return A list of all pending messages
     */
    public List<Message> getPendingMessages() {
        List<Message> result = new ArrayList<>();
        for (Queue<Message> queue : pendingMessages.values()) {
            result.addAll(queue);
        }
        return result;
    }
}
