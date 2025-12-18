package p2p.peer.peerui.login;

/**
 * Holds connection settings for the peer.
 * Default values match those used in the bootstrap-server module.
 */
public record ConnectionSettings(
        String username,
        int udpPort,
        int rmiPort,
        String bootstrapHost,
        int bootstrapPort) {
    /**
     * Default UDP port for heartbeat communication.
     * Must match HeartbeatListener.DEFAULT_UDP_PORT in bootstrap-server.
     */
    public static final int DEFAULT_UDP_PORT = 9876;

    /**
     * Default RMI port for peer service.
     */
    public static final int DEFAULT_RMI_PORT = 1100;

    /**
     * Default bootstrap server host.
     */
    public static final String DEFAULT_BOOTSTRAP_HOST = "localhost";

    /**
     * Default bootstrap server RMI port.
     * Must match BootstrapServer.RMI_PORT in bootstrap-server.
     */
    public static final int DEFAULT_BOOTSTRAP_PORT = 1099;

    /**
     * Creates default connection settings with the given username.
     *
     * @param username The username for the peer
     * @return ConnectionSettings with default values
     */
    public static ConnectionSettings defaults(String username) {
        return new ConnectionSettings(
                username,
                DEFAULT_UDP_PORT,
                DEFAULT_RMI_PORT,
                DEFAULT_BOOTSTRAP_HOST,
                DEFAULT_BOOTSTRAP_PORT);
    }
}
