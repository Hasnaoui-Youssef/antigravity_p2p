package p2p.peer.ui;

import p2p.common.model.User;
import p2p.common.rmi.BootstrapService;
import p2p.peer.friends.FriendManager;
import p2p.peer.messaging.MessageHandler;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

/**
 * Terminal-based user interface for peer interaction.
 */
public class TerminalUI {
    
    private final User localUser;
    private final FriendManager friendManager;
    private final MessageHandler messageHandler;
    private final String bootstrapHost;
    private final int bootstrapPort;
    
    private BootstrapService bootstrapService;
    private boolean running = true;
    
    public TerminalUI(User localUser, FriendManager friendManager, MessageHandler messageHandler,
                     String bootstrapHost, int bootstrapPort) {
        this.localUser = localUser;
        this.friendManager = friendManager;
        this.messageHandler = messageHandler;
        this.bootstrapHost = bootstrapHost;
        this.bootstrapPort = bootstrapPort;
    }
    
    /**
     * Start the terminal UI loop.
     */
    public void start() {
        printWelcome();
        
        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()) {
                    continue;
                }
                
                try {
                    processCommand(input);
                } catch (Exception e) {
                    System.out.println("[Error] " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Process a user command.
     */
    private void processCommand(String input) throws Exception {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        switch (command) {
            case "/login" -> commandLogin();
            case "/search" -> commandSearch(args);
            case "/add" -> commandAddFriend(args);
            case "/accept" -> commandAcceptFriend(args);
            case "/list" -> commandListFriends();
            case "/msg" -> commandSendMessage(args);
            case "/help" -> printHelp();
            case "/quit" -> commandQuit();
            default -> System.out.println("Unknown command. Type /help for available commands.");
        }
    }
    
    private void commandLogin() throws Exception {
        if (bootstrapService != null) {
            System.out.println("[Bootstrap] Already connected");
            return;
        }
        
        Registry registry = LocateRegistry.getRegistry(bootstrapHost, bootstrapPort);
        bootstrapService = (BootstrapService) registry.lookup("BootstrapService");
        bootstrapService.register(localUser);
        
        System.out.println("[Bootstrap] Connected and registered as " + localUser.getUsername());
    }
    
    private void commandSearch(String username) throws Exception {
        if (bootstrapService == null) {
            System.out.println("[Bootstrap] Not connected. Use /login first");
            return;
        }
        
        if (username.isEmpty()) {
            System.out.println("Usage: /search <username>");
            return;
        }
        
        List<User> results = bootstrapService.searchByUsername(username);
        
        if (results.isEmpty()) {
            System.out.println("[Search] No users found matching '" + username + "'");
        } else {
            System.out.println("[Search] Found " + results.size() + " user(s):");
            for (User user : results) {
                if (!user.getUserId().equals(localUser.getUserId())) {
                    System.out.println("  - " + user.getUsername() + " @ " + user.getIpAddress() + ":" + user.getRmiPort());
                }
            }
        }
    }
    
    private void commandAddFriend(String username) throws Exception {
        if (bootstrapService == null) {
            System.out.println("[Bootstrap] Not connected. Use /login first");
            return;
        }
        
        if (username.isEmpty()) {
            System.out.println("Usage: /add <username>");
            return;
        }
        
        List<User> results = bootstrapService.searchByUsername(username);
        User target = results.stream()
            .filter(u -> u.getUsername().equalsIgnoreCase(username) && !u.getUserId().equals(localUser.getUserId()))
            .findFirst()
            .orElse(null);
        
        if (target == null) {
            System.out.println("[Friends] User '" + username + "' not found");
            return;
        }
        
        friendManager.sendFriendRequest(target);
    }
    
    private void commandAcceptFriend(String username) throws Exception {
        if (username.isEmpty()) {
            System.out.println("Usage: /accept <username>");
            return;
        }
        
        User requester = friendManager.getPendingRequests().stream()
            .filter(u -> u.getUsername().equalsIgnoreCase(username))
            .findFirst()
            .orElse(null);
        
        if (requester == null) {
            System.out.println("[Friends] No pending request from '" + username + "'");
            return;
        }
        
        friendManager.acceptFriendRequest(requester);
    }
    
    private void commandListFriends() {
        List<User> friends = friendManager.getFriends();
        List<User> pending = friendManager.getPendingRequests();
        
        System.out.println("\n=== Friends (" + friends.size() + ") ===");
        if (friends.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (User friend : friends) {
                System.out.println("  ✓ " + friend.getUsername());
            }
        }
        
        System.out.println("\n=== Pending Requests (" + pending.size() + ") ===");
        if (pending.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (User user : pending) {
                System.out.println("  ? " + user.getUsername() + " (use /accept " + user.getUsername() + ")");
            }
        }
        System.out.println();
    }
    
    private void commandSendMessage(String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            System.out.println("Usage: /msg <username> <message>");
            return;
        }
        
        String username = parts[0];
        String content = parts[1];
        
        User recipient = friendManager.getFriendByUsername(username);
        if (recipient == null) {
            System.out.println("[Message] '" + username + "' is not your friend");
            return;
        }
        
        messageHandler.sendMessage(recipient, content);
    }
    
    private void commandQuit() throws Exception {
        if (bootstrapService != null) {
            bootstrapService.unregister(localUser.getUserId());
        }
        running = false;
        System.out.println("[Peer] Goodbye!");
    }
    
    private void printWelcome() {
        System.out.println("\n=== Antigravity P2P - Peer Node ===");
        System.out.println("Logged in as: " + localUser.getUsername());
        System.out.println("User ID: " + localUser.getUserId());
        System.out.println("\nType /help for available commands\n");
    }
    
    private void printHelp() {
        System.out.println("\n=== Available Commands ===");
        System.out.println("  /login                    - Connect to bootstrap server");
        System.out.println("  /search <username>        - Search for users");
        System.out.println("  /add <username>           - Send friend request");
        System.out.println("  /accept <username>        - Accept friend request");
        System.out.println("  /list                     - Show friends and pending requests");
        System.out.println("  /msg <username> <message> - Send message to friend");
        System.out.println("  /help                     - Show this help");
        System.out.println("  /quit                     - Exit application");
        System.out.println();
    }
}
