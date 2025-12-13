package p2p.common.rmi;

import java.io.Serial;

/**
 * Exception thrown when attempting to register a user with a username
 * that is already taken by another registered user.
 */
public class UsernameAlreadyExistsException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String username;

    public UsernameAlreadyExistsException(String username) {
        super("Username '" + username + "' is already taken");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
