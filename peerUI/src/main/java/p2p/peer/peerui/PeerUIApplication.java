package p2p.peer.peerui;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main entry point for the PeerUI application.
 * A JavaFX-based UI for the Antigravity P2P network.
 */
public class PeerUIApplication extends Application {

    @Override
    public void start(Stage stage) {
        SceneManager.getInstance().setPrimaryStage(stage);
        SceneManager.getInstance().showLogin();
    }

    @Override
    public void stop() {
        // Clean shutdown - the main controller will handle stopping the peer
        System.out.println("[PeerUI] Application shutting down...");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
