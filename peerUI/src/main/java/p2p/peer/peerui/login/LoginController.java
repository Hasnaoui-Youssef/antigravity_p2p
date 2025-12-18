package p2p.peer.peerui.login;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import p2p.peer.PeerController;
import p2p.peer.peerui.SceneManager;

/**
 * Controller for the login screen.
 * Handles user input validation and peer connection.
 */
public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private TitledPane advancedPane;

    @FXML
    private TextField udpPortField;

    @FXML
    private TextField rmiPortField;

    @FXML
    private TextField bootstrapHostField;

    @FXML
    private TextField bootstrapPortField;

    @FXML
    private Button connectButton;

    private SceneManager sceneManager;

    public void setSceneManager(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        // Set default values
        udpPortField.setText(String.valueOf(ConnectionSettings.DEFAULT_UDP_PORT));
        rmiPortField.setText(String.valueOf(ConnectionSettings.DEFAULT_RMI_PORT));
        bootstrapHostField.setText(ConnectionSettings.DEFAULT_BOOTSTRAP_HOST);
        bootstrapPortField.setText(String.valueOf(ConnectionSettings.DEFAULT_BOOTSTRAP_PORT));

        // Add numeric validation for port fields
        addNumericValidation(udpPortField);
        addNumericValidation(rmiPortField);
        addNumericValidation(bootstrapPortField);
    }

    private void addNumericValidation(TextField field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                field.setText(newVal.replaceAll("\\D", ""));
            }
        });
    }

    @FXML
    private void onConnect() {
        String username = usernameField.getText().trim();

        // Validate username
        if (username.isEmpty()) {
            showError("Validation Error", "Please enter a username.");
            return;
        }

        // Parse connection settings
        ConnectionSettings settings;
        try {
            settings = parseSettings(username);
        } catch (NumberFormatException e) {
            showError("Validation Error", "Invalid port number. Please enter valid numbers.");
            return;
        }

        // Disable button during connection
        connectButton.setDisable(true);
        connectButton.setText("Connecting...");

        // Connect in background thread
        new Thread(() -> {
            try {
                PeerController peerController = new PeerController(
                        settings.username(),
                        settings.rmiPort(),
                        settings.bootstrapHost(),
                        settings.bootstrapPort(),
                        settings.udpPort());
                peerController.start();

                // Switch to main screen on success
                Platform.runLater(() -> {
                    sceneManager.showMain(peerController);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectButton.setDisable(false);
                    connectButton.setText("Connect");
                    showError("Connection Failed", e.getMessage());
                });
            }
        }).start();
    }

    private ConnectionSettings parseSettings(String username) {
        int udpPort = parsePort(udpPortField.getText(), ConnectionSettings.DEFAULT_UDP_PORT);
        int rmiPort = parsePort(rmiPortField.getText(), ConnectionSettings.DEFAULT_RMI_PORT);
        String bootstrapHost = bootstrapHostField.getText().trim();
        if (bootstrapHost.isEmpty()) {
            bootstrapHost = ConnectionSettings.DEFAULT_BOOTSTRAP_HOST;
        }
        int bootstrapPort = parsePort(bootstrapPortField.getText(), ConnectionSettings.DEFAULT_BOOTSTRAP_PORT);

        return new ConnectionSettings(username, udpPort, rmiPort, bootstrapHost, bootstrapPort);
    }

    private int parsePort(String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        int port = Integer.parseInt(text.trim());
        if (port < 1 || port > 65535) {
            throw new NumberFormatException("Port must be between 1 and 65535");
        }
        return port;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
