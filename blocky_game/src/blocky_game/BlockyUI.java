package blocky_game;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import blocky.Level;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Main JavaFX application window synchronizing the Blockly workspace with the EMF model.
 */
public class BlockyUI extends Application {

    static {
        // JavaFX WebView media pipeline crash fix on Windows:
        System.setProperty("com.sun.webkit.useMediaPlayer", "false");
        System.setProperty("com.sun.media.jfxmediaimpl.disableGStreamer", "true");

        // Prism D3D texture pool race condition fixes on Windows (JDK-8352209):
        if (System.getProperty("prism.dirtyopts") == null) {
            System.setProperty("prism.dirtyopts", "false");
        }
        if (System.getProperty("prism.disableRegionCaching") == null) {
            System.setProperty("prism.disableRegionCaching", "true");
        }
        if (System.getProperty("prism.cacheshapes") == null) {
            System.setProperty("prism.cacheshapes", "false");
        }
        if (System.getProperty("prism.primtextures") == null) {
            System.setProperty("prism.primtextures", "false");
        }
        if (System.getProperty("prism.maxvram") == null) {
            System.setProperty("prism.maxvram", "1G");
        }
        if (System.getProperty("prism.targetvram") == null) {
            System.setProperty("prism.targetvram", "512M");
        }
    }

    public static void main(String[] args) {
        Application.launch(BlockyUI.class, args);
    }

    private GameEngine engine;
    private WebView webView;
    private BlockySnapshotService snapshotService;
    private MomotUiCoordinator momotCoordinator;
    private BlockyJsBridgeController bridgeController;

    /**
     * JavaFX WebView uses weak references for window.javaBridge.
     * Keep a strong field reference to prevent garbage collection.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private BlockyJsBridgeController.JSBridge jsBridge;

    @Override
    public void start(Stage primaryStage) {
        System.out.println("[BlockyUI] Application starting...");

        try {
            engine = new GameEngine();
            engine.initializeGame();

            webView = new WebView();
            snapshotService = new BlockySnapshotService(webView);
            momotCoordinator = new MomotUiCoordinator(engine, webView);
            bridgeController = new BlockyJsBridgeController(engine, webView, snapshotService, momotCoordinator);
            jsBridge = bridgeController.getBridge();

            WebEngine webEngine = webView.getEngine();

            // Manual snapshot control overlay (bottom-left corner)
            Button snapshotButton = new Button("Snapshot");
            snapshotButton.setOnAction(e2 -> {
                if (bridgeController.isAwaitingInjectComplete()) {
                    System.err.println("[BlockyUI] Snapshot skipped: WebView injection not complete yet.");
                    return;
                }
                snapshotService.saveWebViewSvgSnapshot();
                snapshotButton.setVisible(false);
                snapshotService.save8kPngSnapshot(primaryStage, result -> {
                    snapshotButton.setVisible(true);
                    if (result.success() && result.outputPath() != null) {
                        result = JavaFxSnapshotExporter.verifyPng(
                                result.outputPath(),
                                JavaFxSnapshotExporter.TARGET_WIDTH,
                                JavaFxSnapshotExporter.TARGET_HEIGHT);
                    }
                    showExportAlert(result);
                });
            });
            StackPane.setAlignment(snapshotButton, Pos.BOTTOM_LEFT);
            StackPane.setMargin(snapshotButton, new Insets(10));

            // Redirect JS console alerts to Java System.out
            webEngine.setOnAlert(event -> System.out.println("[JS Alert] " + event.getData()));

            // Load local maze.html with initial level
            int initialLevel = 1;
            if (engine.getCurrentLevel() != null && engine.getCurrentLevel().getId() >= 1 && engine.getCurrentLevel().getId() <= 10) {
                initialLevel = engine.getCurrentLevel().getId();
            }
            String mazeUrl = getMazeBaseUrl() + "?lang=en&level=" + initialLevel;
            if (Boolean.getBoolean("blocky.levelLimits")) {
                mazeUrl += "&levelLimits=true";
            }
            webEngine.load(mazeUrl);

            // Setup the bridge on page load success
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    System.out.println("[BlockyUI] WebView page load SUCCEEDED: " + webEngine.getLocation());
                    jsBridge = bridgeController.getBridge();
                    bridgeController.setupBridge(webEngine);
                }
            });

            // Menu Bar
            MenuBar menuBar = new MenuBar();
            Menu fileMenu = new Menu("File");
            MenuItem loadItem = new MenuItem("Load XMI...");
            loadItem.setOnAction(e -> loadModelImpl());
            MenuItem saveItem = new MenuItem("Save XMI...");
            saveItem.setOnAction(e -> saveModelImpl());
            MenuItem export8kItem = new MenuItem("Export 8K PNG...");
            export8kItem.setOnAction(e -> export8kPngImpl(primaryStage));
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.setOnAction(e -> Platform.exit());
            fileMenu.getItems().addAll(loadItem, saveItem, export8kItem, exitItem);
            menuBar.getMenus().add(fileMenu);

            StackPane content = new StackPane(webView, snapshotButton);
            BorderPane root = new BorderPane();
            root.setTop(menuBar);
            root.setCenter(content);
            Scene scene = new Scene(root, 1200, 800);

            primaryStage.setTitle("Blockly Games : Maze (Web Sync to XMI)");
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Loads a model XMI via FileChooser and applies it to the WebView. */
    private void loadModelImpl() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Solution Model (XMI)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XMI Files", "*.xmi"));

        File initialDir = new File("blocky_game");
        if (!initialDir.exists()) initialDir = new File(".");
        fileChooser.setInitialDirectory(initialDir);

        Window window = webView != null && webView.getScene() != null ? webView.getScene().getWindow() : null;
        File xmiFile = fileChooser.showOpenDialog(window);

        if (xmiFile == null) {
            webView.getEngine().executeScript("try { if (window.__lvlLoadingHide) window.__lvlLoadingHide(); } catch(e) {}");
            return;
        }

        try {
            engine.loadFromFile(xmiFile);
        } catch (IOException e) {
            webView.getEngine().executeScript("try { if (window.__lvlLoadingHide) window.__lvlLoadingHide(); } catch(e) {}");
            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Failed to load model: " + e.getMessage()).showAndWait());
            return;
        } catch (IllegalArgumentException e) {
            webView.getEngine().executeScript("try { if (window.__lvlLoadingHide) window.__lvlLoadingHide(); } catch(e) {}");
            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Invalid model: " + e.getMessage()).showAndWait());
            return;
        }
        Level level = engine.getCurrentLevel();
        int levelId = level != null ? Math.max(1, Math.min(10, level.getId())) : 1;
        bridgeController.setPendingApplyLevel(true);
        bridgeController.setSuppressSync(true);
        webView.getEngine().load(getMazeBaseUrl() + "?lang=en&level=" + levelId);
    }

    /** Saves the current model XMI via FileChooser. */
    private void saveModelImpl() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Model (XMI)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XMI Files", "*.xmi"));
        fileChooser.setInitialFileName("save.xmi");

        File initialDir = new File("blocky_game");
        if (!initialDir.exists()) initialDir = new File(".");
        fileChooser.setInitialDirectory(initialDir);

        Window window = webView != null && webView.getScene() != null ? webView.getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            engine.saveToFile(file);
            System.out.println("[BlockyUI] Manually saved model to: " + file.getAbsolutePath());
        }

        webView.getEngine().executeScript("try { if (window.__lvlLoadingHide) window.__lvlLoadingHide(); } catch(e) {}");
    }

    /** Exports the current scene as a true 7680×4320 PNG via FileChooser. */
    private void export8kPngImpl(Stage stage) {
        if (stage == null || stage.getScene() == null) {
            showExportAlert(new JavaFxSnapshotExporter.ExportResult(
                    false, null, 0, 0, 0L, "Scene is not ready for export."));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export 8K PNG");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Images", "*.png"));
        fileChooser.setInitialFileName(
                JavaFxSnapshotExporter.defaultTimestampedPath(Path.of(".")).getFileName().toString());

        File initialDir = new File("blocky_game");
        if (!initialDir.exists()) {
            initialDir = new File(".");
        }
        fileChooser.setInitialDirectory(initialDir);

        Window window = webView != null && webView.getScene() != null ? webView.getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);
        if (file == null) {
            return;
        }

        Path outputPath = file.toPath();
        snapshotService.save8kPngSnapshot(
                stage,
                outputPath,
                JavaFxSnapshotExporter.ScalingMode.PRESERVE_ASPECT_RATIO,
                result -> {
                    if (result.success()) {
                        result = JavaFxSnapshotExporter.verifyPng(
                                outputPath,
                                JavaFxSnapshotExporter.TARGET_WIDTH,
                                JavaFxSnapshotExporter.TARGET_HEIGHT);
                    }
                    showExportAlert(result);
                });
    }

    private static void showExportAlert(JavaFxSnapshotExporter.ExportResult result) {
        if (result == null) {
            return;
        }
        Alert.AlertType type = result.success() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
        Alert alert = new Alert(type, result.message());
        Platform.runLater(alert::showAndWait);
    }

    private static String getMazeBaseUrl() {
        File mazeFile = new File("blocky_game/src/blocky_game/blockly-games-web/maze.html");
        if (!mazeFile.exists()) mazeFile = new File("src/blocky_game/blockly-games-web/maze.html");
        if (!mazeFile.exists()) mazeFile = new File("blockly-games-web/maze.html");
        String url = mazeFile.toURI().toString();
        if (url.contains("?")) url = url.substring(0, url.indexOf('?'));
        return url;
    }
}
