package p2p.peer.peerui.notifications;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Manages toast notifications with fade-in/out animations.
 * Notifications appear in the top-right corner and auto-dismiss after a few
 * seconds.
 */
public class NotificationManager {

    private static final Duration FADE_DURATION = Duration.millis(300);
    private static final Duration DISPLAY_DURATION = Duration.seconds(4);
    private static final double NOTIFICATION_WIDTH = 300;
    private static final double SLIDE_DISTANCE = 50;

    private final VBox notificationContainer;

    /**
     * Creates a new NotificationManager.
     *
     * @param notificationContainer The VBox container where notifications will be
     *                              added.
     *                              Should be positioned in top-right corner with
     *                              appropriate styling.
     */
    public NotificationManager(VBox notificationContainer) {
        this.notificationContainer = notificationContainer;
        this.notificationContainer.setSpacing(8);
        this.notificationContainer.setAlignment(Pos.TOP_RIGHT);
        this.notificationContainer.setPickOnBounds(false);
    }

    /**
     * Shows a message notification when a chat message is received.
     *
     * @param senderName Name of the message sender
     * @param preview    Preview of the message content (truncated)
     */
    public void showMessageNotification(String senderName, String preview) {
        String truncatedPreview = preview.length() > 50 ? preview.substring(0, 47) + "..." : preview;
        showNotification("💬 " + senderName, truncatedPreview, "notification-toast");
    }

    /**
     * Shows a notification for an incoming friend request.
     *
     * @param username Username of the requester
     */
    public void showFriendRequestNotification(String username) {
        showNotification("👋 Friend Request", username + " wants to be your friend", "notification-toast");
    }

    /**
     * Shows a notification for a group invitation.
     *
     * @param groupName   Name of the group
     * @param inviterName Name of the person who invited
     */
    public void showGroupInviteNotification(String groupName, String inviterName) {
        showNotification("👥 Group Invitation", inviterName + " invited you to " + groupName, "notification-toast");
    }

    /**
     * Shows an error notification.
     *
     * @param message Error message to display
     */
    public void showErrorNotification(String message) {
        showNotification("⚠ Error", message, "notification-toast", "notification-toast-error");
    }

    /**
     * Shows an error notification with custom title.
     */
    public void showErrorNotification(String title, String message) {
        showNotification(title, message, "notification-toast", "notification-toast-error");
    }

    /**
     * Shows a success notification.
     *
     * @param message Success message to display
     */
    public void showSuccessNotification(String message) {
        showNotification("✓ Success", message, "notification-toast", "notification-toast-success");
    }

    /**
     * Shows a success notification with custom title.
     */
    public void showSuccessNotification(String title, String message) {
        showNotification(title, message, "notification-toast", "notification-toast-success");
    }

    /**
     * Shows a generic notification with fade-in/out animation.
     */
    private void showNotification(String title, String message, String... styleClasses) {
        // Create notification content
        VBox notification = createNotificationNode(title, message, styleClasses);

        // Set initial state for animation
        notification.setOpacity(0);
        notification.setTranslateX(SLIDE_DISTANCE);

        // Add to container
        notificationContainer.getChildren().add(notification);

        // Create fade-in + slide animation
        FadeTransition fadeIn = new FadeTransition(FADE_DURATION, notification);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        TranslateTransition slideIn = new TranslateTransition(FADE_DURATION, notification);
        slideIn.setFromX(SLIDE_DISTANCE);
        slideIn.setToX(0);

        ParallelTransition showAnimation = new ParallelTransition(fadeIn, slideIn);

        // Create fade-out + slide animation
        FadeTransition fadeOut = new FadeTransition(FADE_DURATION, notification);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        TranslateTransition slideOut = new TranslateTransition(FADE_DURATION, notification);
        slideOut.setFromX(0);
        slideOut.setToX(SLIDE_DISTANCE);

        ParallelTransition hideAnimation = new ParallelTransition(fadeOut, slideOut);
        hideAnimation.setOnFinished(e -> notificationContainer.getChildren().remove(notification));

        // Sequence: show -> pause -> hide
        PauseTransition pause = new PauseTransition(DISPLAY_DURATION);

        SequentialTransition sequence = new SequentialTransition(showAnimation, pause, hideAnimation);

        // Click to dismiss immediately
        notification.setOnMouseClicked(e -> {
            sequence.stop();
            hideAnimation.setOnFinished(ev -> notificationContainer.getChildren().remove(notification));
            hideAnimation.playFromStart();
        });

        sequence.play();
    }

    /**
     * Creates the notification node with title and message.
     */
    private VBox createNotificationNode(String title, String message, String... styleClasses) {
        VBox notification = new VBox(4);
        notification.setPrefWidth(NOTIFICATION_WIDTH);
        notification.setMaxWidth(NOTIFICATION_WIDTH);
        notification.getStyleClass().addAll(styleClasses);

        // Title
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("notification-toast-title");

        // Message
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("notification-toast-message");
        messageLabel.setWrapText(true);

        notification.getChildren().addAll(titleLabel, messageLabel);

        // Add hover effect
        notification.setOnMouseEntered(e -> notification.setStyle("-fx-cursor: hand;"));

        return notification;
    }

    /**
     * Clears all current notifications.
     */
    public void clearAll() {
        notificationContainer.getChildren().clear();
    }
}
