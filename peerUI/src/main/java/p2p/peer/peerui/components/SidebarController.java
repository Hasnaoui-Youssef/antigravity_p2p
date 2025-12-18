package p2p.peer.peerui.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import p2p.common.model.Group;
import p2p.common.model.User;
import p2p.common.model.message.ChatMessage;
import p2p.peer.PeerController;
import p2p.peer.peerui.model.Conversation;
import p2p.peer.peerui.model.UnreadTracker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Controller for the sidebar component.
 * Manages friends/groups lists and user search.
 */
public class SidebarController {

    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private ToggleButton friendsTab;
    @FXML
    private ToggleButton groupsTab;
    @FXML
    private ListView<Conversation> contactList;
    @FXML
    private VBox pendingSection;
    @FXML
    private Label pendingBadge;
    @FXML
    private Button newGroupButton;

    private PeerController peerController;
    private UnreadTracker unreadTracker;
    private Consumer<Conversation> onConversationSelected;
    private Runnable onNewGroupRequested;
    private Runnable onViewPendingRequested;

    private boolean showingFriends = true;
    private final ToggleGroup tabGroup = new ToggleGroup();

    // Cache for unread counts to avoid repeated message fetching
    private final Map<String, Integer> unreadCountCache = new ConcurrentHashMap<>();
    private final Map<String, Long> latestMessageCache = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        // Set up toggle group for tabs
        friendsTab.setToggleGroup(tabGroup);
        groupsTab.setToggleGroup(tabGroup);
        friendsTab.setSelected(true);

        // Set up list cell factory
        contactList.setCellFactory(param -> new ContactListCell());

        // Handle selection
        contactList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onConversationSelected != null) {
                // Mark as read when selected
                if (unreadTracker != null) {
                    unreadTracker.markAsRead(newVal.id());
                    // Clear cache for this conversation
                    unreadCountCache.remove(newVal.id());
                }
                onConversationSelected.accept(newVal);
                // Refresh only the selected cell
                contactList.refresh();
            }
        });

        // Search on Enter key
        searchField.setOnAction(e -> onSearch());
    }

    /**
     * Initialize the sidebar with a PeerController.
     */
    public void setPeerController(PeerController peerController) {
        this.peerController = peerController;
        refreshContactList();
        updatePendingSection();
    }

    /**
     * Set the unread tracker for displaying unread indicators.
     */
    public void setUnreadTracker(UnreadTracker unreadTracker) {
        this.unreadTracker = unreadTracker;
    }

    /**
     * Set callback for when a conversation is selected.
     */
    public void setOnConversationSelected(Consumer<Conversation> callback) {
        this.onConversationSelected = callback;
    }

    /**
     * Set callback for when "New Group" button is clicked.
     */
    public void setOnNewGroupRequested(Runnable callback) {
        this.onNewGroupRequested = callback;
    }

    /**
     * Set callback for when "View Pending" is clicked.
     */
    public void setOnViewPendingRequested(Runnable callback) {
        this.onViewPendingRequested = callback;
    }

    @FXML
    private void onFriendsTabSelected() {
        showingFriends = true;
        friendsTab.setSelected(true);
        refreshContactList();
    }

    @FXML
    private void onGroupsTabSelected() {
        showingFriends = false;
        groupsTab.setSelected(true);
        refreshContactList();
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty() || peerController == null) {
            return;
        }

        searchButton.setDisable(true);
        new Thread(() -> {
            try {
                List<User> results = peerController.searchUsers(query);
                // Filter out local user
                results.removeIf(u -> u.username().equals(peerController.getLocalUser().username()));

                // Filter out users we already sent requests to
                Set<String> sentRequests = peerController.getSentRequests();
                results.removeIf(u -> sentRequests.contains(u.username()));

                // Filter out existing friends
                List<User> friends = peerController.getFriends();
                Set<String> friendUsernames = new HashSet<>();
                for (User f : friends)
                    friendUsernames.add(f.username());
                results.removeIf(u -> friendUsernames.contains(u.username()));

                Platform.runLater(() -> {
                    showSearchResults(results);
                    searchButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> searchButton.setDisable(false));
            }
        }).start();
    }

    @FXML
    private void onViewPending() {
        if (onViewPendingRequested != null) {
            onViewPendingRequested.run();
        }
    }

    @FXML
    private void onNewGroup() {
        if (onNewGroupRequested != null) {
            onNewGroupRequested.run();
        }
    }

    /**
     * Refresh the contact list based on current tab selection.
     */
    public void refreshContactList() {
        if (peerController == null) {
            return;
        }

        Platform.runLater(() -> {
            ObservableList<Conversation> items = FXCollections.observableArrayList();

            if (showingFriends) {
                List<User> friends = peerController.getFriends();
                for (User friend : friends) {
                    items.add(Conversation.direct(friend.userId(), friend.username()));
                }
            } else {
                List<Group> groups = peerController.getGroups();
                for (Group group : groups) {
                    items.add(Conversation.group(group.groupId(), group.name()));
                }
            }

            contactList.setItems(items);
        });
    }

    /**
     * Update unread cache for a specific conversation.
     * Called when a new message arrives.
     */
    public void updateUnreadCache(String conversationId) {
        if (peerController == null || unreadTracker == null)
            return;

        // Run on background thread to avoid blocking UI
        new Thread(() -> {
            try {
                List<ChatMessage> messages;
                // Determine if it's a friend or group based on current contacts
                boolean isDirect = showingFriends;
                if (isDirect) {
                    messages = peerController.getFriendMessages(conversationId);
                } else {
                    messages = peerController.getGroupMessages(conversationId);
                }

                if (!messages.isEmpty()) {
                    long latestTimestamp = messages.getLast().getTimestamp();
                    int unreadCount = unreadTracker.getUnreadCount(conversationId, messages);

                    latestMessageCache.put(conversationId, latestTimestamp);
                    unreadCountCache.put(conversationId, unreadCount);

                    Platform.runLater(() -> contactList.refresh());
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    /**
     * Update the pending section visibility and badge count.
     */
    public void updatePendingSection() {
        if (peerController == null) {
            return;
        }

        Platform.runLater(() -> {
            int pendingFriends = peerController.getPendingRequests().size();
            int pendingGroups = peerController.getPendingGroupInvitations().size();
            int total = pendingFriends + pendingGroups;

            if (total > 0) {
                pendingSection.setManaged(true);
                pendingSection.setVisible(true);
                pendingBadge.setText(String.valueOf(total));
            } else {
                pendingSection.setManaged(false);
                pendingSection.setVisible(false);
            }
        });
    }

    /**
     * Show search results in a dialog.
     */
    private void showSearchResults(List<User> results) {
        if (results.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Search Results");
            alert.setHeaderText(null);
            alert.setContentText("No users found.");
            alert.showAndWait();
            return;
        }

        // Create dialog with search results
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Search Results");
        dialog.setHeaderText("Found " + results.size() + " user(s)");

        ListView<User> resultList = new ListView<>(FXCollections.observableArrayList(results));
        resultList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(10);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    Label nameLabel = new Label(user.username());
                    nameLabel.setStyle("-fx-font-weight: bold;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button addButton = new Button("Add Friend");
                    addButton.getStyleClass().add("button-secondary");
                    addButton.setOnAction(e -> {
                        sendFriendRequest(user);
                        dialog.close();
                    });

                    row.getChildren().addAll(nameLabel, spacer, addButton);
                    setGraphic(row);
                }
            }
        });

        dialog.getDialogPane().setContent(resultList);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void sendFriendRequest(User user) {
        if (peerController == null)
            return;

        new Thread(() -> {
            try {
                peerController.sendFriendRequest(user.username());
            } catch (Exception e) {
                // Error will be handled by event listener
            }
        }).start();
    }

    /**
     * Get the currently selected conversation.
     */
    public Conversation getSelectedConversation() {
        return contactList.getSelectionModel().getSelectedItem();
    }

    /**
     * Select a conversation programmatically.
     */
    public void selectConversation(String conversationId) {
        for (Conversation conv : contactList.getItems()) {
            if (conv.id().equals(conversationId)) {
                contactList.getSelectionModel().select(conv);
                break;
            }
        }
    }

    /**
     * Custom list cell for displaying contacts with unread indicators.
     * Uses cached unread counts to avoid expensive message fetching during render.
     */
    private class ContactListCell extends ListCell<Conversation> {
        @Override
        protected void updateItem(Conversation conversation, boolean empty) {
            super.updateItem(conversation, empty);

            if (empty || conversation == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().removeAll("contact-item", "contact-item-unread");
                setMouseTransparent(true);
            } else {
                setMouseTransparent(false);
                HBox row = new HBox(10);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                // Icon
                String icon = conversation.isDirect() ? "👤" : "💬";
                Label iconLabel = new Label(icon);

                // Name
                Label nameLabel = new Label(conversation.displayName());
                nameLabel.getStyleClass().add("contact-name");
                HBox.setHgrow(nameLabel, Priority.ALWAYS);

                row.getChildren().addAll(iconLabel, nameLabel);

                // Use cached unread count instead of fetching messages
                Integer cachedCount = unreadCountCache.get(conversation.id());
                Long cachedTimestamp = latestMessageCache.get(conversation.id());

                boolean hasUnread = false;
                int unreadCount = 0;

                if (cachedCount != null && cachedCount > 0) {
                    hasUnread = true;
                    unreadCount = cachedCount;
                } else if (unreadTracker != null && cachedTimestamp != null) {
                    hasUnread = unreadTracker.hasUnread(conversation.id(), cachedTimestamp);
                }

                // Add unread badge if needed
                if (hasUnread && unreadCount > 0) {
                    Label badge = new Label(String.valueOf(unreadCount));
                    badge.getStyleClass().add("unread-badge");
                    row.getChildren().add(badge);
                    if (!getStyleClass().contains("contact-item-unread")) {
                        getStyleClass().add("contact-item-unread");
                    }
                } else {
                    getStyleClass().remove("contact-item-unread");
                }

                if (!getStyleClass().contains("contact-item")) {
                    getStyleClass().add("contact-item");
                }
                setGraphic(row);
            }
        }
    }
}
