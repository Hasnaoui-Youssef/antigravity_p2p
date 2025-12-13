package p2p.common.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a user in the P2P network.
 * Immutable and serializable for RMI transmission.
 */
public record User(String userId, String username, String ipAddress, int rmiPort) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public User(String userId, String username, String ipAddress, int rmiPort) {
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.ipAddress = Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        this.rmiPort = rmiPort;
    }

    public static User create(String username, String ipAddress, int rmiPort) {
        return new User(UUID.randomUUID().toString(), username, ipAddress, rmiPort);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof User user)) return false;
        return userId.equals(user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return String.format("User{username='%s', userId='%s', ip='%s', port=%d}",
                username, userId, ipAddress, rmiPort);
    }
}
