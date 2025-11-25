package p2p.common.vectorclock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VectorClock implementation.
 */
class VectorClockTest {
    
    private VectorClock clock1;
    private VectorClock clock2;
    
    @BeforeEach
    void setUp() {
        clock1 = new VectorClock();
        clock2 = new VectorClock();
    }
    
    @Test
    @DisplayName("Increment should increase logical time for process")
    void testIncrement() {
        clock1.increment("process1");
        assertEquals(1, clock1.getTime("process1"));
        
        clock1.increment("process1");
        assertEquals(2, clock1.getTime("process1"));
        
        clock1.increment("process2");
        assertEquals(1, clock1.getTime("process2"));
    }
    
    @Test
    @DisplayName("Update should take maximum of each entry")
    void testUpdate() {
        clock1.increment("p1");
        clock1.increment("p1");
        clock1.increment("p2");
        
        clock2.increment("p1");
        clock2.increment("p3");
        clock2.increment("p3");
        
        clock1.update(clock2);
        
        assertEquals(2, clock1.getTime("p1")); // max(2, 1) = 2
        assertEquals(1, clock1.getTime("p2")); // max(1, 0) = 1
        assertEquals(2, clock1.getTime("p3")); // max(0, 2) = 2
    }
    
    @Test
    @DisplayName("HappensBefore should detect causal ordering")
    void testHappensBefore() {
        // clock1: {p1: 1, p2: 1}
        clock1.increment("p1");
        clock1.increment("p2");
        
        // clock2: {p1: 2, p2: 2}
        clock2.increment("p1");
        clock2.increment("p1");
        clock2.increment("p2");
        clock2.increment("p2");
        
        assertTrue(clock1.happensBefore(clock2));
        assertFalse(clock2.happensBefore(clock1));
    }
    
    @Test
    @DisplayName("HappensBefore should return false for equal clocks")
    void testHappensBeforeEqual() {
        clock1.increment("p1");
        clock2.increment("p1");
        
        assertFalse(clock1.happensBefore(clock2));
        assertFalse(clock2.happensBefore(clock1));
    }
    
    @Test
    @DisplayName("IsConcurrentWith should detect concurrent events")
    void testConcurrentEvents() {
        // clock1: {p1: 2, p2: 1}
        clock1.increment("p1");
        clock1.increment("p1");
        clock1.increment("p2");
        
        // clock2: {p1: 1, p2: 2}
        clock2.increment("p1");
        clock2.increment("p2");
        clock2.increment("p2");
        
        assertTrue(clock1.isConcurrentWith(clock2));
        assertTrue(clock2.isConcurrentWith(clock1));
    }
    
    @Test
    @DisplayName("Clone should create independent copy")
    void testClone() {
        clock1.increment("p1");
        clock1.increment("p2");
        
        VectorClock clone = clock1.clone();
        
        assertEquals(clock1.getTime("p1"), clone.getTime("p1"));
        assertEquals(clock1.getTime("p2"), clone.getTime("p2"));
        
        // Modify original
        clock1.increment("p1");
        
        // Clone should not be affected
        assertEquals(2, clock1.getTime("p1"));
        assertEquals(1, clone.getTime("p1"));
    }
    
    @Test
    @DisplayName("Thread safety test with concurrent increments")
    void testThreadSafety() throws InterruptedException {
        final int numThreads = 10;
        final int incrementsPerThread = 100;
        
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final String processId = "p1";
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    clock1.increment(processId);
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals(numThreads * incrementsPerThread, clock1.getTime("p1"));
    }
    
    @Test
    @DisplayName("GetClockSnapshot should return map copy")
    void testGetClockSnapshot() {
        clock1.increment("p1");
        clock1.increment("p2");
        
        var snapshot = clock1.getClockSnapshot();
        
        assertEquals(1, snapshot.get("p1"));
        assertEquals(1, snapshot.get("p2"));
        
        // Modify original
        clock1.increment("p1");
        
        // Snapshot should not change
        assertEquals(1, snapshot.get("p1"));
    }
    
    @Test
    @DisplayName("Equals should compare clock values")
    void testEquals() {
        clock1.increment("p1");
        clock1.increment("p2");
        
        clock2.increment("p1");
        clock2.increment("p2");
        
        assertEquals(clock1, clock2);
        
        clock2.increment("p3");
        assertNotEquals(clock1, clock2);
    }
}
