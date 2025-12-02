package p2p.bootstrap;

import p2p.common.model.User;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe registry of active users with heartbeat-based expiration.
 */
public class UserRegistry {

    private static final long HEARTBEAT_TIMEOUT_MS = 30_000; // 30 seconds

    private final Map<String, UserEntry> users = new ConcurrentHashMap<>();
    private final Thread cleanupThread;
    private volatile boolean running = true;

    public UserRegistry() {
        // Start background thread to remove stale users
        cleanupThread = new Thread(this::cleanupLoop, "UserRegistry-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * Add or update a user in the registry.
     */
    public void addUser(User user) {
        users.put(user.userId(), new UserEntry(user, Instant.now()));
        System.out.println("[Registry] User registered: " + user.username());
    }

    /**
     * Remove a user from the registry.
     */
    public void removeUser(String userId) {
        UserEntry entry = users.remove(userId);
        if (entry != null) {
            System.out.println("[Registry] User unregistered: " + entry.user.username());
        }
    }

    /**
     * Get a user by ID.
     */
    public User getUser(String userId) {
        UserEntry entry = users.get(userId);
        return entry != null ? entry.user : null;
    }

    /**
     * Update heartbeat timestamp for a user.
     */
    public void updateHeartbeat(String userId) {
        UserEntry entry = users.get(userId);
        if (entry != null) {
            entry.lastHeartbeat = Instant.now();
        }
    }

    /**
     * Search users by username (case-insensitive partial match).
     */
    public List<User> searchByUsername(String username) {
        String query = username.toLowerCase();
        return users.values().stream()
            .map(entry -> entry.user)
            .filter(user -> user.username().toLowerCase().contains(query))
            .collect(Collectors.toList());
    }

    /**
     * Search users by IP address (partial match).
     */
    public List<User> searchByIp(String ip) {
        return users.values().stream()
            .map(entry -> entry.user)
            .filter(user -> user.ipAddress().contains(ip))
            .collect(Collectors.toList());
    }

    /**
     * Get all active users.
     */
    public List<User> getAllUsers() {
        return users.values().stream()
            .map(entry -> entry.user)
            .collect(Collectors.toList());
    }

    /**
     * Cleanup loop that removes stale users.
     */
    private void cleanupLoop() {
        while (running) {
            try {
                Thread.sleep(10_000); // Check every 10 seconds

                Instant now = Instant.now();
                List<String> staleUserIds = new ArrayList<>();

                users.forEach((userId, entry) -> {
                    long timeSinceHeartbeat = now.toEpochMilli() - entry.lastHeartbeat.toEpochMilli();
                    if (timeSinceHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        staleUserIds.add(userId);
                    }
                });

                staleUserIds.forEach(userId -> {
                    UserEntry entry = users.remove(userId);
                    if (entry != null) {
                        System.out.println("[Registry] Removed stale user: " + entry.user.username());
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void shutdown() {
        running = false;
        cleanupThread.interrupt();
    }

    /**
     * Internal class to store user with heartbeat timestamp.
     */
    private static class UserEntry {
        final User user;
        volatile Instant lastHeartbeat;

        UserEntry(User user, Instant lastHeartbeat) {
            this.user = user;
            this.lastHeartbeat = lastHeartbeat;
        }
    }
}
