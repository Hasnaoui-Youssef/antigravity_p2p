package p2p.common.vectorclock;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of a vector clock for tracking causality
 * in distributed systems.
 */
public class VectorClock implements Serializable, Cloneable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Integer> clock;

    public VectorClock() {
        this.clock = new ConcurrentHashMap<>();
    }

    private VectorClock(Map<String, Integer> clock) {
        this.clock = new ConcurrentHashMap<>(clock);
    }

    /**
     * Increment the logical time for the given process ID.
     */
    public synchronized void increment(String processId) {
        clock.merge(processId, 1, Integer::sum);
    }

    /**
     * Update this clock with another clock (take maximum of each entry).
     * Used when receiving a message to update local clock.
     */
    public synchronized void update(VectorClock other) {
        if (other == null) return;

        for (Map.Entry<String, Integer> entry : other.clock.entrySet()) {
            String processId = entry.getKey();
            int otherTime = entry.getValue();
            clock.merge(processId, otherTime, Math::max);
        }
    }

    /**
     * Check if this clock happens before another clock.
     * Returns true if this <= other and this != other.
     */
    public synchronized boolean happensBefore(VectorClock other) {
        if (other == null) return false;

        boolean lessThanOrEqual = true;
        boolean strictlyLess = false;

        // Check all entries in this clock
        for (Map.Entry<String, Integer> entry : clock.entrySet()) {
            String processId = entry.getKey();
            int thisTime = entry.getValue();
            int otherTime = other.clock.getOrDefault(processId, 0);

            if (thisTime > otherTime) {
                lessThanOrEqual = false;
                break;
            }
            if (thisTime < otherTime) {
                strictlyLess = true;
            }
        }

        // Check entries only in other clock
        for (Map.Entry<String, Integer> entry : other.clock.entrySet()) {
            String processId = entry.getKey();
            if (!clock.containsKey(processId) && entry.getValue() > 0) {
                strictlyLess = true;
            }
        }

        return lessThanOrEqual && strictlyLess;
    }

    /**
     * Check if events are concurrent (neither happens before the other).
     */
    public synchronized boolean isConcurrentWith(VectorClock other) {
        return !happensBefore(other) && !other.happensBefore(this);
    }

    public synchronized int getTime(String processId) {
        return clock.getOrDefault(processId, 0);
    }

    public synchronized Map<String, Integer> getClockSnapshot() {
        return new HashMap<>(clock);
    }

    @Override
    public synchronized VectorClock clone() {
        return new VectorClock(new HashMap<>(clock));
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VectorClock that = (VectorClock) o;
        return clock.equals(that.clock);
    }

    @Override
    public synchronized int hashCode() {
        return Objects.hash(clock);
    }

    @Override
    public synchronized String toString() {
        return clock.toString();
    }
}
