package p2p.peer.peerui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import p2p.peer.PeerController;
import p2p.peer.peerui.login.LoginController;
import p2p.peer.peerui.main.MainController;

import java.io.IOException;

/**
 * Manages scene transitions for the PeerUI application.
 * Singleton pattern to ensure consistent stage management.
 */
public class SceneManager {

    private static SceneManager instance;
    private Stage primaryStage;

    private SceneManager() {
    }

    public static SceneManager getInstance() {
        if (instance == null) {
            instance = new SceneManager();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        this.primaryStage.setTitle("Antigravity P2P");
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Shows the login screen.
     */
    public void showLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/p2p/peer/peerui/login/login-view.fxml"));
            Parent root = loader.load();

            LoginController controller = loader.getController();
            controller.setSceneManager(this);

            Scene scene = new Scene(root, 400, 500);
            scene.getStylesheets().add(
                    getClass().getResource("/p2p/peer/peerui/styles/application.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load login view", e);
        }
    }

    /**
     * Shows the main screen with the connected PeerController.
     *
     * @param peerController The connected peer controller
     */
    public void showMain(PeerController peerController) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/p2p/peer/peerui/main/main-view.fxml"));
            Parent root = loader.load();

            MainController controller = loader.getController();
            controller.setSceneManager(this);
            controller.setPeerController(peerController);

            Scene scene = new Scene(root, 800, 600);
            scene.getStylesheets().add(
                    getClass().getResource("/p2p/peer/peerui/styles/application.css").toExternalForm());

            primaryStage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load main view", e);
        }
    }
}
