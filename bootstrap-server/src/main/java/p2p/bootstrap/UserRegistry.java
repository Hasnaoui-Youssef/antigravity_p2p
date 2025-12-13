package p2p.bootstrap;

import p2p.common.model.User;
import p2p.common.rmi.UsernameAlreadyExistsException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe registry of active users with heartbeat-based expiration.
 */
public class UserRegistry {

    private static final long HEARTBEAT_TIMEOUT_MS = 30_000; // 30 seconds
    private static final long CLEANUP_INTERVAL_SECONDS = 10;

    private final Map<String, UserEntry> users = new ConcurrentHashMap<>();
    // Secondary index for username lookups (lowercase username -> userId)
    private final Map<String, String> usernameIndex = new ConcurrentHashMap<>();
    private final ReadWriteLock registrationLock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService scheduler;

    public UserRegistry() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UserRegistry-Cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::cleanupStaleUsers,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Register a user in the registry.
     * Validates that the username is not already taken by another user.
     *
     * @param user The user to register
     * @throws UsernameAlreadyExistsException if the username is already taken
     */
    public void registerUser(User user) throws UsernameAlreadyExistsException {
        Objects.requireNonNull(user, "user must not be null");

        String normalizedUsername = user.username().toLowerCase();

        registrationLock.writeLock().lock();
        try {
            // Check if username is taken by a different user
            String existingUserId = usernameIndex.get(normalizedUsername);
            if (existingUserId != null && !existingUserId.equals(user.userId())) {
                throw new UsernameAlreadyExistsException(user.username());
            }

            // Register the user
            users.put(user.userId(), new UserEntry(user, Instant.now()));
            usernameIndex.put(normalizedUsername, user.userId());
            System.out.println("[Registry] User registered: " + user.username());
        } finally {
            registrationLock.writeLock().unlock();
        }
    }

    /**
     * Add or update a user in the registry (legacy method for backward compatibility).
     * For new registrations, use registerUser() which validates username uniqueness.
     */
    public void addUser(User user) {
        Objects.requireNonNull(user, "user must not be null");
        try {
            registerUser(user);
        } catch (UsernameAlreadyExistsException e) {
            // For backward compatibility, log warning but allow update if same userId
            System.out.println("[Registry] Warning: " + e.getMessage());
        }
    }

    /**
     * Check if a username is available for registration.
     *
     * @param username The username to check
     * @return true if available, false if taken
     */
    public boolean isUsernameAvailable(String username) {
        if (username == null) {
            return false;
        }
        registrationLock.readLock().lock();
        try {
            return !usernameIndex.containsKey(username.toLowerCase());
        } finally {
            registrationLock.readLock().unlock();
        }
    }

    /**
     * Remove a user from the registry.
     */
    public void removeUser(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        registrationLock.writeLock().lock();
        try {
            UserEntry entry = users.remove(userId);
            if (entry != null) {
                usernameIndex.remove(entry.user.username().toLowerCase());
                System.out.println("[Registry] User unregistered: " + entry.user.username());
            }
        } finally {
            registrationLock.writeLock().unlock();
        }
    }

    /**
     * Get a user by ID.
     */
    public User getUser(String userId) {
        if (userId == null) {
            return null;
        }
        UserEntry entry = users.get(userId);
        return entry != null ? entry.user : null;
    }

    /**
     * Update heartbeat timestamp for a user.
     */
    public void updateHeartbeat(String userId) {
        if (userId == null) {
            return;
        }
        UserEntry entry = users.get(userId);
        if (entry != null) {
            entry.updateHeartbeat();
        }
    }

    /**
     * Search users by username (case-insensitive partial match).
     */
    public List<User> searchByUsername(String username) {
        if (username == null) {
            return List.of();
        }
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
        if (ip == null) {
            return List.of();
        }
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
     * Removes users that haven't sent a heartbeat within the timeout period.
     */
    private void cleanupStaleUsers() {
        Instant now = Instant.now();
        List<String> staleUserIds = new ArrayList<>();

        users.forEach((userId, entry) -> {
            long timeSinceHeartbeat = now.toEpochMilli() - entry.getLastHeartbeatMillis();
            if (timeSinceHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                staleUserIds.add(userId);
            }
        });

        if (staleUserIds.isEmpty()) {
            return;
        }

        registrationLock.writeLock().lock();
        try {
            for (String userId : staleUserIds) {
                UserEntry entry = users.remove(userId);
                if (entry != null) {
                    usernameIndex.remove(entry.user.username().toLowerCase());
                    System.out.println("[Registry] Removed stale user: " + entry.user.username());
                }
            }
        } finally {
            registrationLock.writeLock().unlock();
        }
    }

    /**
     * Shutdown the cleanup scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal class to store user with heartbeat timestamp.
     * Uses volatile for thread-safe heartbeat updates.
     */
    private static class UserEntry {
        final User user;
        private volatile long lastHeartbeatMillis;

        UserEntry(User user, Instant lastHeartbeat) {
            this.user = user;
            this.lastHeartbeatMillis = lastHeartbeat.toEpochMilli();
        }

        void updateHeartbeat() {
            this.lastHeartbeatMillis = System.currentTimeMillis();
        }

        long getLastHeartbeatMillis() {
            return lastHeartbeatMillis;
        }
    }
}
