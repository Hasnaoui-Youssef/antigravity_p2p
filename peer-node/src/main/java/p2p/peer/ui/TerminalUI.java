package p2p.peer.ui;

import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.peer.PeerController;

import java.util.List;
import java.util.Scanner;

/**
 * Terminal-based user interface with conversation view support.
 */
public class TerminalUI {

    private final PeerController controller;

    private boolean running = true;
    private UIState state = UIState.MAIN_MENU;
    private ConversationContext activeConversation = null;

    public TerminalUI(PeerController controller) {
        this.controller = controller;
    }

    /**
     * Start the terminal UI loop.
     */
    public void start() {
        printWelcome();

        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                printPrompt();
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

    private void printPrompt() {
        if (state == UIState.CONVERSATION_VIEW && activeConversation != null) {
            System.out.print("[" + activeConversation.getDisplayName() + "] > ");
        } else {
            System.out.print("> ");
        }
    }

    /**
     * Process a user command.
     */
    private void processCommand(String input) throws Exception {
        // Check for state-independent commands
        if (input.equalsIgnoreCase("/help")) {
            printHelp();
            return;
        }

        if (input.equalsIgnoreCase("/quit")) {
            commandQuit();
            return;
        }

        // Handle based on state
        if (state == UIState.MAIN_MENU) {
            processMainMenuCommand(input);
        } else if (state == UIState.CONVERSATION_VIEW) {
            processConversationCommand(input);
        }
    }

    private void processMainMenuCommand(String input) throws Exception {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "/login" -> commandLogin();
            case "/list" -> commandList();
            case "/search" -> commandSearch(args);
            case "/add" -> commandAddFriend(args);
            case "/accept" -> commandAcceptFriend(args);
            case "/open" -> commandOpen(args);
            case "/create" -> commandCreateGroup(args);
            default -> System.out.println("Unknown command. Type /help for available commands.");
        }
    }

    private void processConversationCommand(String input) throws Exception {
        if (input.equalsIgnoreCase("/back")) {
            commandBack();
            return;
        }

        // Any other input is a message
        commandSendMessage(input);
    }

    // ========== Main Menu Commands ==========

    private void commandLogin() throws Exception {
        controller.start();

        System.out.println("[Bootstrap] Connected and registered as " + controller.getLocalUser().getUsername());
    }

    private void commandList() {
        List<User> friends = controller.getFriends();
        List<User> pending = controller.getPendingRequests();
        List<Group> groups = controller.getGroups();

        System.out.println("\n=== Friends (" + friends.size() + ") ===");
        if (friends.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (User friend : friends) {
                System.out.println("  ✓ " + friend.getUsername());
            }
        }

        System.out.println("\n=== Groups (" + groups.size() + ") ===");
        if (groups.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (Group group : groups) {
                String leader = group.getLeaderId().equals(controller.getLocalUser().getUserId()) ? " (Leader)" : "";
                System.out.println("  # " + group.getName() + leader + " - " + group.getMembers().size() + " members");
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

    private void commandSearch(String username) throws Exception {
        if (username.isEmpty()) {
            System.out.println("Usage: /search <username>");
            return;
        }

        List<User> results = controller.searchUsers(username);

        if (results.isEmpty()) {
            System.out.println("[Search] No users found matching '" + username + "'");
        } else {
            for (User user : results) {
                if (!user.getUserId().equals(controller.getLocalUser().getUserId())) {
                    System.out.println(
                            "  - " + user.getUsername() + " @ " + user.getIpAddress() + ":" + user.getRmiPort());
                }
            }
        }
    }

    private void commandAddFriend(String username) throws Exception {
        if (username.isEmpty()) {
            System.out.println("Usage: /add <username>");
            return;
        }

        controller.sendFriendRequest(username);
        System.out.println("[Friends] Sent friend request to " + username);
    }

    private void commandAcceptFriend(String username) throws Exception {
        if (username.isEmpty()) {
            System.out.println("Usage: /accept <username>");
            return;
        }

        controller.acceptFriendRequest(username);
        System.out.println("[Friends] Accepted request from " + username);
    }

    private void commandOpen(String target) {
        if (target.isEmpty()) {
            System.out.println("Usage: /open <username|group>");
            return;
        }

        // Try to find friend
        User friend = controller.getFriends().stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(target))
                .findFirst()
                .orElse(null);

        if (friend != null) {
            activeConversation = ConversationContext.directMessage(friend.getUserId(), friend.getUsername());
            state = UIState.CONVERSATION_VIEW;
            System.out.println("[Chat] Opened conversation with " + friend.getUsername());
            System.out.println("Type /back to return to main menu");
            return;
        }

        // Try to find group
        Group group = controller.getGroups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(target))
                .findFirst()
                .orElse(null);

        if (group != null) {
            activeConversation = ConversationContext.groupChat(group.getGroupId(), group.getName());
            state = UIState.CONVERSATION_VIEW;
            System.out.println("[Chat] Opened group " + group.getName());
            System.out.println("Type /back to return to main menu");
            return;
        }

        System.out.println("[Error] Not found: " + target);
    }

    private void commandCreateGroup(String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            System.out.println("Usage: /create <group-name> <friend1> [friend2] ...");
            return;
        }

        String groupName = parts[0];
        String[] friendNames = parts[1].split("\\s+");

        Group group = controller.createGroup(groupName, List.of(friendNames));
        System.out.println("[Group] Created group '" + groupName + "' with " + group.getMembers().size() + " members");
    }

    // ========== Conversation Commands ==========

    private void commandBack() {
        activeConversation = null;
        state = UIState.MAIN_MENU;
        System.out.println("[Menu] Returned to main menu");
    }

    private void commandSendMessage(String content) throws Exception {
        if (activeConversation == null) {
            return;
        }

        if (activeConversation.getType() == ConversationContext.Type.DIRECT) {
            User friend = controller.getFriends().stream()
                    .filter(u -> u.getUserId().equals(activeConversation.getId()))
                    .findFirst()
                    .orElse(null);

            if (friend != null) {
                controller.sendMessage(friend.getUsername(), content);
            }
        } else if (activeConversation.getType() == ConversationContext.Type.GROUP) {
            controller.sendGroupMessage(activeConversation.getId(), content);
            System.out.println("[You] " + content);
        }
    }

    private void commandQuit() throws Exception {
        controller.stop();
        running = false;
        System.out.println("[Peer] Goodbye!");
    }

    private void printWelcome() {
        System.out.println("\n=== Antigravity P2P - Peer Node ===");
        System.out.println("Logged in as: " + controller.getLocalUser().getUsername());
        System.out.println("User ID: " + controller.getLocalUser().getUserId());
        System.out.println("\nType /help for available commands\n");
    }

    private void printHelp() {
        System.out.println("\n=== Main Menu Commands ===");
        System.out.println("  /login                          - Connect to bootstrap server");
        System.out.println("  /list                           - Show friends, groups, and requests");
        System.out.println("  /search <username>              - Search for users");
        System.out.println("  /add <username>                 - Send friend request");
        System.out.println("  /accept <username>              - Accept friend request");
        System.out.println("  /open <username|group>          - Open conversation");
        System.out.println("  /create <name> <friend1> ...    - Create group with friends");
        System.out.println("  /help                           - Show this help");
        System.out.println("  /quit                           - Exit application");

        if (state == UIState.CONVERSATION_VIEW) {
            System.out.println("\n=== Conversation Commands ===");
            System.out.println("  <message>                       - Send message");
            System.out.println("  /back                           - Return to main menu");
        }
        System.out.println();
    }
}
