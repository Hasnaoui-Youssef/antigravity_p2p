package p2p.peer.peerui.main;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import p2p.common.model.Group;
import p2p.common.model.GroupEvent;
import p2p.common.model.User;
import p2p.common.model.message.ChatMessage;
import p2p.common.model.message.GroupInvitationMessage;
import p2p.common.model.message.Message;
import p2p.peer.PeerController;
import p2p.peer.PeerEventListener;
import p2p.peer.peerui.SceneManager;
import p2p.peer.peerui.components.ChatAreaController;
import p2p.peer.peerui.components.SidebarController;
import p2p.peer.peerui.model.Conversation;
import p2p.peer.peerui.model.UnreadTracker;
import p2p.peer.peerui.notifications.NotificationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for the main screen.
 * Manages the connected peer session and coordinates between components.
 */
public class MainController implements PeerEventListener {

    @FXML
    private Label usernameLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox notificationContainer;

    // Injected sub-controllers from fx:include
    @FXML
    private SidebarController sidebarController;
    @FXML
    private ChatAreaController chatAreaController;

    private SceneManager sceneManager;
    private PeerController peerController;
    private NotificationManager notificationManager;
    private UnreadTracker unreadTracker;

    @FXML
    public void initialize() {
        unreadTracker = new UnreadTracker();
    }

    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public void setPeerController(PeerController peerController) {
        this.peerController = peerController;

        if (peerController != null) {
            // Update UI with user info
            usernameLabel.setText(peerController.getLocalUser().username());
            statusLabel.setText("Connected");

            // Register as event listener
            peerController.addEventListener(this);

            // Initialize notification manager
            notificationManager = new NotificationManager(notificationContainer);

            // Initialize sidebar
            sidebarController.setPeerController(peerController);
            sidebarController.setUnreadTracker(unreadTracker);
            sidebarController.setOnConversationSelected(this::onConversationSelected);
            sidebarController.setOnNewGroupRequested(this::showNewGroupDialog);
            sidebarController.setOnViewPendingRequested(this::showPendingDialog);

            // Initialize chat area
            chatAreaController.setPeerController(peerController);
        }
    }

    @FXML
    private void onDisconnect() {
        if (peerController != null) {
            peerController.removeEventListener(this);
            peerController.stop();
            peerController = null;
        }
        unreadTracker.clear();
        sceneManager.showLogin();
    }

    /**
     * Called when a conversation is selected in the sidebar.
     */
    private void onConversationSelected(Conversation conversation) {
        chatAreaController.setConversation(conversation);
        // Mark as read
        if (conversation != null) {
            unreadTracker.markAsRead(conversation.id());
        }
    }

    /**
     * Show dialog to create a new group.
     */
    private void showNewGroupDialog() {
        if (peerController == null)
            return;

        List<User> friends = peerController.getFriends();
        if (friends.size() < 2) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cannot Create Group");
            alert.setHeaderText(null);
            alert.setContentText("You need at least 2 friends to create a group.");
            alert.showAndWait();
            return;
        }

        // Get group name
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Create Group");
        nameDialog.setHeaderText("Enter a name for the new group");
        nameDialog.setContentText("Group name:");

        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().trim().isEmpty()) {
            return;
        }

        String groupName = nameResult.get().trim();

        // Show friend selection dialog
        Alert selectionDialog = new Alert(Alert.AlertType.CONFIRMATION);
        selectionDialog.setTitle("Select Members");
        selectionDialog.setHeaderText("Select at least 2 friends for: " + groupName);

        VBox content = new VBox(8);
        List<javafx.scene.control.CheckBox> checkboxes = new ArrayList<>();

        for (User friend : friends) {
            javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(friend.username());
            cb.setUserData(friend.username());
            checkboxes.add(cb);
            content.getChildren().add(cb);
        }

        selectionDialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = selectionDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            List<String> selectedFriends = checkboxes.stream()
                    .filter(javafx.scene.control.CheckBox::isSelected)
                    .map(cb -> (String) cb.getUserData())
                    .collect(Collectors.toList());

            if (selectedFriends.size() < 2) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Not Enough Members");
                alert.setHeaderText(null);
                alert.setContentText("Please select at least 2 friends.");
                alert.showAndWait();
                return;
            }

            createGroup(groupName, selectedFriends);
        }
    }

    private void createGroup(String name, List<String> friendUsernames) {
        new Thread(() -> {
            try {
                Group group = peerController.createGroup(name, friendUsernames);
                Platform.runLater(() -> {
                    sidebarController.refreshContactList();
                    notificationManager.showSuccessNotification("Group '" + name + "' created!");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    notificationManager.showErrorNotification("Failed to create group: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Show dialog for pending friend requests and group invitations.
     */
    private void showPendingDialog() {
        if (peerController == null)
            return;

        List<User> pendingFriends = peerController.getPendingRequests();
        List<GroupInvitationMessage> pendingGroups = peerController.getPendingGroupInvitations();

        if (pendingFriends.isEmpty() && pendingGroups.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Pending Items");
            alert.setHeaderText(null);
            alert.setContentText("You have no pending friend requests or group invitations.");
            alert.showAndWait();
            return;
        }

        // Use a dialog that we can close programmatically
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Pending");
        dialog.setHeaderText("Pending Requests & Invitations");

        VBox content = new VBox(16);

        // Friend requests
        if (!pendingFriends.isEmpty()) {
            Label friendLabel = new Label("👋 Friend Requests");
            friendLabel.setStyle("-fx-font-weight: bold;");
            content.getChildren().add(friendLabel);

            for (User user : pendingFriends) {
                javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label nameLabel = new Label(user.username());
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                javafx.scene.control.Button acceptBtn = new javafx.scene.control.Button("Accept");
                acceptBtn.getStyleClass().add("button");
                acceptBtn.setOnAction(e -> {
                    acceptFriendRequest(user.username());
                    dialog.close();
                });

                javafx.scene.control.Button rejectBtn = new javafx.scene.control.Button("Reject");
                rejectBtn.getStyleClass().add("button-secondary");
                rejectBtn.setOnAction(e -> {
                    rejectFriendRequest(user.username());
                    dialog.close();
                });

                row.getChildren().addAll(nameLabel, spacer, acceptBtn, rejectBtn);
                content.getChildren().add(row);
            }
        }

        // Group invitations
        if (!pendingGroups.isEmpty()) {
            if (!pendingFriends.isEmpty()) {
                content.getChildren().add(new javafx.scene.control.Separator());
            }

            Label groupLabel = new Label("👥 Group Invitations");
            groupLabel.setStyle("-fx-font-weight: bold;");
            content.getChildren().add(groupLabel);

            for (GroupInvitationMessage invite : pendingGroups) {
                javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                String groupName = invite.getGroupName();
                Label nameLabel = new Label(groupName + " (GroupID: " + invite.getGroupId().substring(0, 8) + "...)");
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                javafx.scene.control.Button acceptBtn = new javafx.scene.control.Button("Accept");
                acceptBtn.getStyleClass().add("button");
                acceptBtn.setOnAction(e -> {
                    acceptGroupInvitation(invite.getGroupId());
                    dialog.close();
                });

                javafx.scene.control.Button rejectBtn = new javafx.scene.control.Button("Reject");
                rejectBtn.getStyleClass().add("button-secondary");
                rejectBtn.setOnAction(e -> {
                    rejectGroupInvitation(invite.getGroupId());
                    dialog.close();
                });

                row.getChildren().addAll(nameLabel, spacer, acceptBtn, rejectBtn);
                content.getChildren().add(row);
            }
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void acceptFriendRequest(String username) {
        new Thread(() -> {
            try {
                peerController.acceptFriendRequest(username);
                Platform.runLater(() -> {
                    sidebarController.refreshContactList();
                    sidebarController.updatePendingSection();
                });
            } catch (Exception e) {
                Platform.runLater(() -> notificationManager.showErrorNotification(e.getMessage()));
            }
        }).start();
    }

    private void rejectFriendRequest(String username) {
        new Thread(() -> {
            try {
                peerController.rejectFriendRequest(username);
                Platform.runLater(() -> sidebarController.updatePendingSection());
            } catch (Exception e) {
                Platform.runLater(() -> notificationManager.showErrorNotification(e.getMessage()));
            }
        }).start();
    }

    private void acceptGroupInvitation(String groupId) {
        new Thread(() -> {
            try {
                peerController.acceptGroupInvitation(groupId);
                Platform.runLater(() -> {
                    sidebarController.refreshContactList();
                    sidebarController.updatePendingSection();
                });
            } catch (Exception e) {
                Platform.runLater(() -> notificationManager.showErrorNotification(e.getMessage()));
            }
        }).start();
    }

    private void rejectGroupInvitation(String groupId) {
        new Thread(() -> {
            try {
                peerController.rejectGroupInvitation(groupId);
                Platform.runLater(() -> sidebarController.updatePendingSection());
            } catch (Exception e) {
                Platform.runLater(() -> notificationManager.showErrorNotification(e.getMessage()));
            }
        }).start();
    }

    // ==================== PeerEventListener Implementation ====================

    @Override
    public void onFriendRequest(User requester) {
        Platform.runLater(() -> {
            sidebarController.updatePendingSection();
            notificationManager.showFriendRequestNotification(requester.username());
        });
    }

    @Override
    public void onFriendRequestAccepted(User accepter) {
        Platform.runLater(() -> {
            sidebarController.refreshContactList();
            notificationManager.showSuccessNotification(accepter.username() + " accepted your friend request!");
        });
    }

    @Override
    public void onMessageReceived(Message message) {
        if (!(message instanceof ChatMessage chatMsg))
            return;

        Platform.runLater(() -> {
            // Determine conversation ID
            String conversationId;
            String senderName = chatMsg.getSenderUsername();

            if (chatMsg.getSubtopic() == ChatMessage.ChatSubtopic.DIRECT) {
                conversationId = chatMsg.getSenderId();
            } else {
                conversationId = chatMsg.getGroupId();
            }

            // Check if this is the active conversation
            Conversation current = chatAreaController.getCurrentConversation();
            boolean isActiveConversation = current != null && current.id().equals(conversationId);

            if (isActiveConversation) {
                // Append to chat view
                chatAreaController.appendMessage(chatMsg);
                // Mark as read since we're viewing it
                unreadTracker.markAsRead(conversationId);
            } else {
                // Show notification for inactive conversation
                String preview = chatMsg.getContent();
                if (chatMsg.getSubtopic() == ChatMessage.ChatSubtopic.GROUP) {
                    Group group = peerController.getGroup(chatMsg.getGroupId());
                    String groupName = group != null ? group.name() : "Group";
                    notificationManager.showMessageNotification(groupName + " · " + senderName, preview);
                } else {
                    notificationManager.showMessageNotification(senderName, preview);
                }
            }

            // Update sidebar unread cache efficiently
            sidebarController.updateUnreadCache(conversationId);
        });
    }

    @Override
    public void onGroupInvitation(GroupInvitationMessage request) {
        Platform.runLater(() -> {
            sidebarController.updatePendingSection();
            notificationManager.showGroupInviteNotification(
                    request.getGroupName(),
                    "Group ID: " + request.getGroupId().substring(0, 8) + "...");
        });
    }

    @Override
    public void onGroupEvent(String groupId, GroupEvent eventType, String message) {
        Platform.runLater(() -> {
            sidebarController.refreshContactList();

            // Show notification for important events
            switch (eventType) {
                case DISSOLVED -> notificationManager.showErrorNotification("Group was dissolved");
                case USER_LEFT, USER_JOINED -> {
                    // Refresh chat header if viewing this group
                    Conversation current = chatAreaController.getCurrentConversation();
                    if (current != null && current.id().equals(groupId)) {
                        chatAreaController.refreshMessages();
                    }
                }
            }
        });
    }

    @Override
    public void onLeaderElected(String groupId, String leaderId, long epoch) {
        Platform.runLater(() -> {
            // Refresh chat header if viewing this group
            Conversation current = chatAreaController.getCurrentConversation();
            if (current != null && current.id().equals(groupId)) {
                chatAreaController.refreshMessages();
            }
        });
    }

    @Override
    public void onError(String message, Throwable t) {
        Platform.runLater(() -> {
            if (notificationManager != null) {
                notificationManager.showErrorNotification(message);
            }
        });
    }

    @Override
    public void onLog(String message) {
        // Could be used for a debug panel in the future
        System.out.println("[UI Log] " + message);
    }
}
