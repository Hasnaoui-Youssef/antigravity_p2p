package p2p.peer.peerui.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import p2p.common.model.Group;
import p2p.common.model.message.ChatMessage;
import p2p.peer.PeerController;
import p2p.peer.peerui.model.Conversation;
import p2p.peer.peerui.model.ConversationType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for the chat area component.
 * Displays messages and handles message sending.
 */
public class ChatAreaController {

    @FXML
    private HBox chatHeader;
    @FXML
    private Label conversationName;
    @FXML
    private Label conversationInfo;
    @FXML
    private Button optionsButton;
    @FXML
    private VBox emptyState;
    @FXML
    private ListView<ChatMessage> messageList;
    @FXML
    private HBox inputArea;
    @FXML
    private TextField messageInput;
    @FXML
    private Button sendButton;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private PeerController peerController;
    private Conversation currentConversation;
    private String localUserId;

    @FXML
    public void initialize() {
        // Set up message list cell factory
        messageList.setCellFactory(param -> new MessageListCell());

        // Focus message input when area is clicked
        messageList.setOnMouseClicked(e -> messageInput.requestFocus());
    }

    /**
     * Set the PeerController reference.
     */
    public void setPeerController(PeerController peerController) {
        this.peerController = peerController;
        if (peerController != null) {
            this.localUserId = peerController.getLocalUser().userId();
        }
    }

    /**
     * Set the current conversation to display.
     */
    public void setConversation(Conversation conversation) {
        this.currentConversation = conversation;

        if (conversation == null) {
            showEmptyState();
            return;
        }

        showChatView();
        updateHeader();
        loadMessages();
    }

    /**
     * Get the current conversation.
     */
    public Conversation getCurrentConversation() {
        return currentConversation;
    }

    /**
     * Append a new message to the current conversation.
     * Used for real-time message updates.
     */
    public void appendMessage(ChatMessage message) {
        if (currentConversation == null)
            return;

        // Check if this message belongs to current conversation
        boolean belongsHere = false;
        if (currentConversation.isDirect()) {
            belongsHere = message.getSubtopic() == ChatMessage.ChatSubtopic.DIRECT &&
                    (message.getSenderId().equals(currentConversation.id()) ||
                            message.getReceiverId().equals(currentConversation.id()));
        } else {
            belongsHere = message.getSubtopic() == ChatMessage.ChatSubtopic.GROUP &&
                    message.getGroupId().equals(currentConversation.id());
        }

        if (belongsHere) {
            Platform.runLater(() -> {
                messageList.getItems().add(message);
                scrollToBottom();
            });
        }
    }

    /**
     * Refresh messages for the current conversation.
     */
    public void refreshMessages() {
        if (currentConversation != null) {
            loadMessages();
        }
    }

    @FXML
    private void onSendMessage() {
        String content = messageInput.getText().trim();
        if (content.isEmpty() || peerController == null || currentConversation == null) {
            return;
        }

        messageInput.clear();
        sendButton.setDisable(true);

        new Thread(() -> {
            try {
                if (currentConversation.isDirect()) {
                    // For direct messages, we need to get the username
                    String username = currentConversation.displayName();
                    peerController.sendMessage(username, content);
                } else {
                    peerController.sendGroupMessage(currentConversation.id(), content);
                }

                Platform.runLater(() -> {
                    sendButton.setDisable(false);
                    loadMessages(); // Refresh to show sent message
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    sendButton.setDisable(false);
                    showError("Failed to send message: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void onOptions() {
        if (currentConversation == null || peerController == null)
            return;

        ContextMenu menu = new ContextMenu();

        if (currentConversation.isGroup()) {
            MenuItem leaveItem = new MenuItem("Leave Group");
            leaveItem.setOnAction(e -> leaveGroup());
            menu.getItems().add(leaveItem);

            // Show group info
            Group group = peerController.getGroup(currentConversation.id());
            if (group != null) {
                MenuItem membersItem = new MenuItem("View Members (" + group.activeMembers().size() + ")");
                membersItem.setOnAction(e -> showGroupMembers(group));
                menu.getItems().add(0, membersItem);
            }
        }

        if (!menu.getItems().isEmpty()) {
            menu.show(optionsButton, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    private void showEmptyState() {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        messageList.setVisible(false);
        messageList.setManaged(false);
        inputArea.setVisible(false);
        inputArea.setManaged(false);
        optionsButton.setVisible(false);
        optionsButton.setManaged(false);

        conversationName.setText("Select a conversation");
        conversationInfo.setText("");
    }

    private void showChatView() {
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        messageList.setVisible(true);
        messageList.setManaged(true);
        inputArea.setVisible(true);
        inputArea.setManaged(true);
        optionsButton.setVisible(true);
        optionsButton.setManaged(true);
    }

    private void updateHeader() {
        if (currentConversation == null)
            return;

        conversationName.setText(currentConversation.displayName());

        if (currentConversation.isGroup() && peerController != null) {
            Group group = peerController.getGroup(currentConversation.id());
            if (group != null) {
                int memberCount = group.activeMembers().size();
                String leaderName = group.leader().username();
                conversationInfo.setText(memberCount + " members · Leader: " + leaderName);
            }
        } else {
            conversationInfo.setText("Direct message");
        }
    }

    private void loadMessages() {
        if (peerController == null || currentConversation == null)
            return;

        Platform.runLater(() -> {
            List<ChatMessage> messages;

            if (currentConversation.isDirect()) {
                messages = peerController.getFriendMessages(currentConversation.id());
            } else {
                messages = peerController.getGroupMessages(currentConversation.id());
            }

            ObservableList<ChatMessage> items = FXCollections.observableArrayList(messages);
            messageList.setItems(items);
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        if (!messageList.getItems().isEmpty()) {
            messageList.scrollTo(messageList.getItems().size() - 1);
        }
    }

    private void leaveGroup() {
        if (currentConversation == null || !currentConversation.isGroup())
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Leave Group");
        confirm.setHeaderText("Leave " + currentConversation.displayName() + "?");
        confirm.setContentText("You will no longer receive messages from this group.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        peerController.leaveGroup(currentConversation.id());
                        Platform.runLater(() -> setConversation(null));
                    } catch (Exception e) {
                        Platform.runLater(() -> showError("Failed to leave group: " + e.getMessage()));
                    }
                }).start();
            }
        });
    }

    private void showGroupMembers(Group group) {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Group Members");
        info.setHeaderText(group.name());

        StringBuilder content = new StringBuilder();
        content.append("👑 ").append(group.leader().username()).append(" (Leader)\n");
        group.members().forEach(member -> content.append("👤 ").append(member.username()).append("\n"));

        info.setContentText(content.toString());
        info.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Custom list cell for message bubbles.
     */
    private class MessageListCell extends ListCell<ChatMessage> {

        @Override
        protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                boolean isSent = message.getSenderId().equals(localUserId);

                VBox bubble = new VBox(4);
                bubble.getStyleClass().add("message-bubble");
                bubble.getStyleClass().add(isSent ? "message-bubble-sent" : "message-bubble-received");
                bubble.setMaxWidth(350);

                // Sender name (for received messages in groups)
                if (!isSent && currentConversation != null && currentConversation.isGroup()) {
                    Label senderLabel = new Label(message.getSenderUsername());
                    senderLabel.getStyleClass().add("message-sender");
                    bubble.getChildren().add(senderLabel);
                }

                // Message content
                Label contentLabel = new Label(message.getContent());
                contentLabel.setWrapText(true);
                contentLabel.getStyleClass().add("message-content");
                bubble.getChildren().add(contentLabel);

                // Timestamp
                String timeStr = formatTimestamp(message.getTimestamp());
                Label timeLabel = new Label(timeStr);
                timeLabel.getStyleClass().add("message-time");
                bubble.getChildren().add(timeLabel);

                // Alignment container
                HBox container = new HBox();
                container.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                container.getChildren().add(bubble);
                HBox.setHgrow(container, Priority.ALWAYS);

                setGraphic(container);
                setStyle("-fx-background-color: transparent; -fx-padding: 4 8;");
            }
        }

        private String formatTimestamp(long timestamp) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            LocalDateTime now = LocalDateTime.now();

            if (dateTime.toLocalDate().equals(now.toLocalDate())) {
                return dateTime.format(TIME_FORMATTER);
            } else {
                return dateTime.format(DATE_TIME_FORMATTER);
            }
        }
    }
}
