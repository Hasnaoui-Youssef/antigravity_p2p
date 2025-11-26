package p2p.peer;

import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.consensus.ConsensusManager;
import p2p.peer.friends.FriendManager;
import p2p.peer.groups.GroupManager;
import p2p.peer.messaging.GossipManager;
import p2p.peer.messaging.MessageHandler;
import p2p.peer.network.HeartbeatSender;
import p2p.peer.network.PeerServiceImpl;
import p2p.peer.network.RMIServer;
import p2p.peer.ui.TerminalUI;

import java.net.InetAddress;

/**
 * Main peer node application.
 * Coordinates all subsystems: RMI, heartbeat, friends, messaging, and UI.
 */
public class PeerNode {
    
    private static final int DEFAULT_BOOTSTRAP_RMI_PORT = 1099;
    private static final int DEFAULT_BOOTSTRAP_UDP_PORT = 9876;
    
    public static void main(String[] args) {
        try {
            // Parse command-line arguments
            CommandLineArgs cmdArgs = parseArguments(args);
            
            // Create local user
            String localIp = InetAddress.getLocalHost().getHostAddress();
            User localUser = User.create(cmdArgs.username, localIp, cmdArgs.port);
            
            // Initialize vector clock
            VectorClock vectorClock = new VectorClock();
            vectorClock.increment(localUser.getUserId());
            
            // Create managers
            FriendManager friendManager = new FriendManager(localUser, vectorClock);
            GroupManager groupManager = new GroupManager(localUser, vectorClock, friendManager);
            MessageHandler messageHandler = new MessageHandler(localUser, vectorClock, friendManager);
            GossipManager gossipManager = new GossipManager(localUser, groupManager);
            ConsensusManager consensusManager = new ConsensusManager(localUser, groupManager);
            
            // Start RMI server
            PeerServiceImpl peerService = new PeerServiceImpl(localUser, vectorClock, friendManager, messageHandler, groupManager, gossipManager, consensusManager);
            RMIServer rmiServer = new RMIServer(cmdArgs.port, "PeerService");
            rmiServer.start(peerService);
            
            // Start heartbeat sender
            HeartbeatSender heartbeatSender = new HeartbeatSender(
                localUser, cmdArgs.bootstrapHost, DEFAULT_BOOTSTRAP_UDP_PORT);
            Thread heartbeatThread = new Thread(heartbeatSender, "HeartbeatSender");
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            
            // Start gossip manager
            gossipManager.start();
            
            // Create PeerController for UI
            PeerController controller = new PeerController(cmdArgs.username, cmdArgs.port, cmdArgs.bootstrapHost, DEFAULT_BOOTSTRAP_RMI_PORT);
            controller.start();
            
            // Setup shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Peer] Shutting down...");
                try {
                    controller.stop();
                } catch (Exception e) {
                    // Ignore
                }
                gossipManager.stop();
                heartbeatSender.stop();
                rmiServer.stop();
            }));
            
            // Start terminal UI (blocks until user quits)
            TerminalUI ui = new TerminalUI(controller, cmdArgs.bootstrapHost, DEFAULT_BOOTSTRAP_RMI_PORT);
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
        String username = null;
        int port = 5000;
        String bootstrapHost = "localhost";
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--username":
                    if (i + 1 < args.length) username = args[++i];
                    break;
                case "--port":
                    if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
                    break;
                case "--bootstrap":
                    if (i + 1 < args.length) {
                        String bootstrap = args[++i];
                        bootstrapHost = bootstrap.contains(":") ? 
                            bootstrap.split(":")[0] : bootstrap;
                    }
                    break;
            }
        }
        
        if (username == null) {
            System.err.println("Usage: PeerNode --username <name> [--port <port>] [--bootstrap <host:port>]");
            System.exit(1);
        }
        
        return new CommandLineArgs(username, port, bootstrapHost);
    }
    
    /**
     * Command-line arguments record.
     */
    private record CommandLineArgs(String username, int port, String bootstrapHost) {}
}
