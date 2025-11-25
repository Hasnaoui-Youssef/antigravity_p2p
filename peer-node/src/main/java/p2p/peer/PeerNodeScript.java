package p2p.peer;

import p2p.common.model.User;
import p2p.common.vectorclock.VectorClock;
import p2p.peer.friends.FriendManager;
import p2p.peer.messaging.MessageHandler;
import p2p.peer.network.HeartbeatSender;
import p2p.peer.network.PeerServiceImpl;
import p2p.peer.network.RMIServer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

/**
 * Non-interactive peer node for automated testing.
 * Accepts commands from a file specified via --script parameter.
 */
public class PeerNodeScript {
    
    private static final int DEFAULT_BOOTSTRAP_RMI_PORT = 1099;
    private static final int DEFAULT_BOOTSTRAP_UDP_PORT = 9876;
    
    public static void main(String[] args) {
        try {
            // Parse command-line arguments
            CommandLineArgs cmdArgs = parseArguments(args);
            
            if (cmdArgs.scriptFile == null) {
                System.err.println("Error: --script <file> parameter required");
                System.exit(1);
            }
            
            // Create local user
            String localIp = InetAddress.getLocalHost().getHostAddress();
            User localUser = User.create(cmdArgs.username, localIp, cmdArgs.port);
            
            // Initialize vector clock
            VectorClock vectorClock = new VectorClock();
            vectorClock.increment(localUser.getUserId());
            
            // Create subsystems
            FriendManager friendManager = new FriendManager(localUser, vectorClock);
            MessageHandler messageHandler = new MessageHandler(localUser, vectorClock, friendManager);
            
            // Start RMI server
            PeerServiceImpl peerService = new PeerServiceImpl(localUser, vectorClock, friendManager, messageHandler);
            RMIServer rmiServer = new RMIServer(cmdArgs.port, "PeerService");
            rmiServer.start(peerService);
            
            // Start heartbeat sender
            HeartbeatSender heartbeatSender = new HeartbeatSender(
                localUser, cmdArgs.bootstrapHost, DEFAULT_BOOTSTRAP_UDP_PORT);
            Thread heartbeatThread = new Thread(heartbeatSender, "HeartbeatSender");
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            
            System.out.println("[Script] Peer initialized: " + localUser.getUsername());
            
            // Execute script commands
            executeScript(cmdArgs.scriptFile, localUser, friendManager, messageHandler, 
                         cmdArgs.bootstrapHost, DEFAULT_BOOTSTRAP_RMI_PORT);
            
            // Cleanup
            heartbeatSender.stop();
            rmiServer.stop();
            
            System.out.println("[Script] Completed successfully");
            
        } catch (Exception e) {
            System.err.println("[Script] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void executeScript(String scriptFile, User localUser, 
                                     FriendManager friendManager, MessageHandler messageHandler,
                                     String bootstrapHost, int bootstrapPort) throws Exception {
        
        try (BufferedReader reader = new BufferedReader(new FileReader(scriptFile))) {
            String line;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                System.out.println("[Script:" + lineNum + "] " + line);
                
                // Process command
                processCommand(line, localUser, friendManager, messageHandler, bootstrapHost, bootstrapPort);
                
                // Small delay between commands
                Thread.sleep(500);
            }
        }
    }
    
    private static void processCommand(String command, User localUser,
                                      FriendManager friendManager, MessageHandler messageHandler,
                                      String bootstrapHost, int bootstrapPort) throws Exception {
        
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1] : "";
        
        switch (cmd) {
            case "SLEEP":
                int ms = Integer.parseInt(args);
                Thread.sleep(ms);
                System.out.println("[Action] Slept for " + ms + "ms");
                break;
                
            case "SEARCH":
                // Would need bootstrap service reference
                System.out.println("[Action] Search: " + args);
                break;
                
            case "ADD_FRIEND":
                // Would need to look up user first
                System.out.println("[Action] Add friend: " + args);
                break;
                
            case "SEND_MESSAGE":
                String[] msgParts = args.split("\\s+", 2);
                if (msgParts.length < 2) {
                    System.err.println("[Error] SEND_MESSAGE requires: username message");
                    return;
                }
                User recipient = friendManager.getFriendByUsername(msgParts[0]);
                if (recipient != null) {
                    messageHandler.sendMessage(recipient, msgParts[1]);
                    System.out.println("[Action] Sent message to " + msgParts[0]);
                } else {
                    System.err.println("[Error] User not a friend: " + msgParts[0]);
                }
                break;
                
            case "LIST_FRIENDS":
                System.out.println("[Action] Friends: " + friendManager.getFriends().size());
                break;
                
            default:
                System.err.println("[Error] Unknown command: " + cmd);
        }
    }
    
    private static CommandLineArgs parseArguments(String[] args) {
        String username = null;
        int port = 5000;
        String bootstrapHost = "localhost";
        String scriptFile = null;
        
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
                case "--script":
                    if (i + 1 < args.length) scriptFile = args[++i];
                    break;
            }
        }
        
        if (username == null) {
            System.err.println("Usage: PeerNodeScript --username <name> --script <file> [--port <port>] [--bootstrap <host:port>]");
            System.exit(1);
        }
        
        return new CommandLineArgs(username, port, bootstrapHost, scriptFile);
    }
    
    private record CommandLineArgs(String username, int port, String bootstrapHost, String scriptFile) {}
}
