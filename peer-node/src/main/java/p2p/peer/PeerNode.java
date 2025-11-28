package p2p.peer;

import p2p.peer.ui.TerminalUI;

/**
 * Main peer node application.
 * Coordinates all subsystems: RMI, heartbeat, friends, messaging, and UI.
 */
public class PeerNode {

    public static void main(String[] args) {
        try {
            CommandLineArgs cmdArgs = parseArguments(args);

            PeerController controller = new PeerController(cmdArgs.username, cmdArgs.port, cmdArgs.host,
                    cmdArgs.bootstrapPort, cmdArgs.udpPort);
            controller.start();

            // Setup shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Peer] Shutting down...");
                try {
                    controller.stop();
                } catch (Exception e) {
                    // Ignore
                }
            }));

            TerminalUI ui = new TerminalUI(controller);
            ui.start();

        } catch (Exception e) {
            System.err.println("[Peer] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Parse command-line arguments.
     */
    private static CommandLineArgs parseArguments(String[] args) {
        final int DEFAULT_BOOTSTRAP_RMI_PORT = 1099;
        final int DEFAULT_BOOTSTRAP_UDP_PORT = 9876;
        String username = null;
        int port = 5000;
        String host = "localhost";
        int bootstrapPort = DEFAULT_BOOTSTRAP_RMI_PORT;
        int udpPort = DEFAULT_BOOTSTRAP_UDP_PORT;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--username":
                    if (i + 1 < args.length)
                        username = args[++i];
                    break;
                case "--local-port":
                    if (i + 1 < args.length)
                        port = Integer.parseInt(args[++i]);
                    break;
                case "--host":
                    if (i + 1 < args.length)
                        host = args[++i];
                    break;
                case "--bootstrap-port":
                    if (i + 1 < args.length)
                        bootstrapPort = Integer.parseInt(args[++i]);
                    break;
                case "--udp-port":
                    if (i + 1 < args.length)
                        udpPort = Integer.parseInt(args[++i]);
                    break;
            }
        }

        if (username == null) {
            System.err.println(
                    "Usage: PeerNode --username <name> [--port <port>] [--host <host>] [--bootstrap-port <port>] [--udp-port <port>]");
            System.exit(1);
        }

        return new CommandLineArgs(username, port, host, bootstrapPort, udpPort);
    }

    /**
     * Command-line arguments record.
     */
    private record CommandLineArgs(String username, int port, String host, int bootstrapPort, int udpPort) {
    }
}
