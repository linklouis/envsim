package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Food;
import edu.msoe.envsim.utilities.apputils.AgentInspectorPane;
import edu.msoe.envsim.utilities.apputils.ControlPanel;
import edu.msoe.envsim.utilities.apputils.EnvironmentManagerDialog;
import edu.msoe.envsim.utilities.apputils.MainMenuPane;
import edu.msoe.envsim.utilities.apputils.TelemetryPanel;
import edu.msoe.envsim.utilities.simutils.AgentData;
import edu.msoe.envsim.utilities.apputils.GameSnapshot;
import edu.msoe.envsim.utilities.simutils.TerrainType;
import edu.msoe.envsim.utilities.Vector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static javafx.scene.control.PopupControl.USE_PREF_SIZE;

public class App extends Application {
//    // Styles shared with AgentInspectorPane so all floating panels look the same
//    private static final String PANEL_STYLE =
//            "-fx-background-color: rgba(30, 30, 30, 0.9); -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8;";
//    private static final String TITLE_STYLE = "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;";
//    private static final String CAPTION_STYLE = "-fx-text-fill: #aaa; -fx-font-weight: bold;";
//    private static final String VALUE_STYLE = "-fx-text-fill: white;";
//    private static final String BUTTON_STYLE = "-fx-background-color: #444; -fx-text-fill: white; -fx-cursor: hand;";
//    private static final String SELECTED_BUTTON_STYLE = "-fx-background-color: #3b5998; -fx-text-fill: white; -fx-cursor: hand; -fx-border-color: #88c0d0; -fx-border-width: 2; -fx-border-radius: 4;";
//    // Slider tick labels pull their color from this looked-up color
//    private static final String SLIDER_STYLE = "-fx-text-background-color: white;";

    private static final int INITIAL_AGENT_COUNT = 12000;
    private static final int INITIAL_FOOD_COUNT = 9000;

    private Environment environment;
    private boolean debugMode = false;

    private TelemetryPanel telemetryPanel;

    private MainMenuPane mainMenuPane;
    /** Whether the player has started a game yet. Decides if the main menu offers Resume. */
    private boolean gameStarted = false;

    private ControlPanel controlPanel;

    @Override
    public void start(@NotNull Stage stage) {
        int viewWidth = 1080;
        int viewHeight = 720;

        AgentInspectorPane inspectorPane = new AgentInspectorPane(() -> environment.clearSelectedAgent(), agent -> environment.goToAgent(agent));
        StackPane.setAlignment(inspectorPane, Pos.TOP_RIGHT);
        StackPane.setMargin(inspectorPane, new Insets(15));
        inspectorPane.maximumHeightLimitProperty().bind(stage.heightProperty().subtract(80));

        // Environment must exist before the menu is built, since the sim speed slider pushes its value into it
        setupEnvironment(stage, viewWidth, viewHeight, inspectorPane);

        // Layer Controls over Canvas using StackPane
        StackPane gamePane = new StackPane();
        controlPanel = new ControlPanel(stage, gamePane, environment, this::showMainMenu);

        StackPane.setAlignment(environment, Pos.TOP_LEFT);
        gamePane.getChildren().addAll(environment, controlPanel, inspectorPane);

        // The world is only populated once the player starts a new game. Until then the menu covers everything.
        mainMenuPane = new MainMenuPane(gamePane, environment, new MainMenuPane.Actions() {
            @Override
            public void startNewGame(@NotNull File terrainFile) {
                App.this.startNewGame(terrainFile);
            }

            @Override
            public @NotNull CompletableFuture<Void> saveGame(@NotNull File saveFile) {
                return App.this.saveGame(saveFile);
            }

            @Override
            public @NotNull CompletableFuture<Void> loadGame(@NotNull File saveFile) {
                return App.this.loadGame(saveFile);
            }

            @Override
            public void quit() {
                App.this.quit();
            }
        });
        mainMenuPane.open(false);

        setupDebugPane();

        VBox root  = new VBox();
        root.getChildren().addAll(telemetryPanel, gamePane);

        Scene scene = new Scene(root, viewWidth, viewHeight);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        stage.setScene(scene);
        stage.setTitle("EnvSim");
        stage.setOnCloseRequest(_ -> quit());
        stage.show();

        setupKeyboardInputs(scene, environment);
        setupClickAwayClose(scene, controlPanel, inspectorPane);
    }

    /**
     * Replaces the current world with a fresh one on the given terrain and starts the simulation.
     */
    private void startNewGame(@NotNull File terrainFile) {
        try {
            environment.startNewWorld(terrainFile, () -> {
                for (int agentIndex = 0; agentIndex < INITIAL_AGENT_COUNT; agentIndex++) {
                    Color randomColor = Color.color(Math.random(), Math.random(), Math.random());
                    new Agent(Math.random() * 30 + 3, randomColor, environment);
                }

                for (int foodIndex = 0; foodIndex < INITIAL_FOOD_COUNT; foodIndex++) {
                    Vector position = Vector.random(Vector.ZERO, environment.getSize());
                    new Food(position, Math.pow(Math.random(), 4) * 40 + 5, environment);
                }
            });
        } catch (Exception exception) {
            showErrorPopup("Could Not Start Game", "Could not load the selected terrain.", exception.getMessage());
            return;
        }

        gameStarted = true;
        controlPanel.setOpen(false);
        mainMenuPane.close();
        environment.resetCamera(environment.getWidth());
        environment.startSimulation();
    }

    /**
     * Captures the game right now, which is quick and has to happen here, and writes it to disk in the background.
     * The game isn't affected while the file is being written.
     */
    private @NotNull CompletableFuture<Void> saveGame(@NotNull File saveFile) {
        GameSnapshot snapshot = environment.captureSnapshot();
        return CompletableFuture.runAsync(() -> {
            try {
                GameSnapshot.write(snapshot, saveFile);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /**
     * Reads the save in the background, then swaps it in on the JavaFX thread.
     * If anything goes wrong the current game is left as it was and the returned future fails.
     */
    private @NotNull CompletableFuture<Void> loadGame(@NotNull File saveFile) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return GameSnapshot.read(saveFile, Agent.getExpectedBrainInputCount());
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                })
                .thenAcceptAsync(snapshot -> {
                    environment.loadSnapshot(snapshot);
                    gameStarted = true;
                    controlPanel.setOpen(false);
                    mainMenuPane.close();
                    environment.startSimulation();
                }, Platform::runLater);
    }

    private void showMainMenu() {
        controlPanel.setOpen(false);
        mainMenuPane.open(gameStarted);
    }

    private void quit() {
        Platform.exit();
        System.exit(0);
    }

    private void setupDebugPane() {
        telemetryPanel = new TelemetryPanel(environment);
        toggleDebugMode(debugMode);
    }

    /**
     * Closes the menu when the user clicks anywhere outside of it. Clicks on the menu itself (including the
     * hamburger button) and on the agent inspector are ignored. The click is not consumed, so it still
     * reaches whatever was clicked (selecting an agent, panning, etc.).
     * <p>
     * In edit mode the panel stays open, since clicking the canvas there paints terrain and the editor
     * controls need to stay reachable.
     */
    private void setupClickAwayClose(@NotNull Scene scene, @NotNull Node menuLayer, @NotNull Node inspectorPane) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!controlPanel.isOpen() || controlPanel.isEditing()) return;

            Object target = event.getTarget();
            if (target instanceof Node node
                    && !isWithin(node, menuLayer)
                    && !isWithin(node, inspectorPane)) {
                controlPanel.setOpen(false);
            }
        });
    }

    private static boolean isWithin(Node node, Node container) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == container) return true;
        }
        return false;
    }

    private void setupKeyboardInputs(@NotNull Scene scene, Environment environment) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (mainMenuPane.isShowing()) {
                    mainMenuPane.onEscapePressed();
                } else {
                    showMainMenu();
                }
                event.consume();
                return;
            }
            // The game's hotkeys shouldn't fire on the world hiding behind the menu
            if (mainMenuPane.isShowing()) return;

            switch (event.getCode()) {
                case KeyCode.HOME -> {
                    toggleDebugMode();
                    event.consume();
                }
                case KeyCode.SPACE -> {
                    environment.toggleSimulation();
                    event.consume();
                }
                case KeyCode.E -> {
                    environment.toggleEditMode();
                    event.consume();
                }
                case KeyCode.R -> {
                    controlPanel.toggleSpeedPreset();
                    event.consume();
                }
                case KeyCode.DIGIT1 -> selectTerrainWithHotkey(event, 0);
                case KeyCode.DIGIT2 -> selectTerrainWithHotkey(event, 1);
                case KeyCode.DIGIT3 -> selectTerrainWithHotkey(event, 2);
                case KeyCode.DIGIT4 -> selectTerrainWithHotkey(event, 3);
                case KeyCode.OPEN_BRACKET -> adjustBrushWithHotkey(event, -1);
                case KeyCode.CLOSE_BRACKET -> adjustBrushWithHotkey(event, 1);
            }
        });
    }

    /**
     * While editing terrain, the number keys pick the terrain in the order the palette lists them.
     */
    private void selectTerrainWithHotkey(@NotNull javafx.scene.input.KeyEvent event, int terrainIndex) {
        if (!environment.isEditMode()) return;
        controlPanel.selectTerrain(TerrainType.values()[terrainIndex]);
        event.consume();
    }

    /**
     * While editing terrain, the bracket keys make the brush smaller and bigger.
     */
    private void adjustBrushWithHotkey(@NotNull javafx.scene.input.KeyEvent event, int delta) {
        if (!environment.isEditMode()) return;
        controlPanel.adjustBrushSize(delta);
        event.consume();
    }

    private void setupEnvironment(@NotNull Stage stage, int viewWidth, int viewHeight, AgentInspectorPane inspectorPane) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double aspectRatio = screenBounds.getHeight() / screenBounds.getWidth();
        int worldWidth = (int) (7000 * 9.0);
        int worldHeight = (int) (worldWidth * aspectRatio);
        double zoom = screenBounds.getWidth() / worldWidth;

        Environment.createDefaultEnvironments(worldWidth, worldHeight);

        // The control panel hides the editor tools when leaving edit mode, and shows them (and opens) when entering it
        Runnable onSimModeStart = () -> {
            if (controlPanel != null) controlPanel.onModeChanged(false);
        };
        Runnable onEditModeStart = () -> {
            if (controlPanel != null) controlPanel.onModeChanged(true);
        };

        environment = new Environment(viewWidth, viewHeight, worldWidth, worldHeight, zoom, debugMode,
                onSimModeStart, onEditModeStart,
                () -> {
                    if (telemetryPanel != null) {
                        telemetryPanel.updateMetrics();
                    }
                },
                agent -> {
                    if (agent != null) {
                        inspectorPane.showAgent(agent);
                    } else {
                        inspectorPane.hide(); // Clicked empty space
                    }
                }
        );

        environment.widthProperty().bind(stage.widthProperty());
        environment.heightProperty().bind(stage.heightProperty());
    }

    private void showErrorPopup(String titleText, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titleText);
        alert.setHeaderText(headerText);

        Label contentLabel = new Label(contentText != null ? contentText : "An unexpected error occurred.");
        contentLabel.getStyleClass().add("value-label");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(380);

        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setContent(contentLabel);
        dialogPane.setPrefWidth(450);

        // Match the rest of the UI (.panel style class and stylesheet)
        dialogPane.getStyleClass().add("panel");
        if (environment != null && environment.getScene() != null) {
            String css = environment.getScene().getStylesheets().stream().findFirst().orElse(null);
            if (css != null && !dialogPane.getStylesheets().contains(css)) {
                dialogPane.getStylesheets().add(css);
            }
        }

        alert.showAndWait();
    }

    private void toggleDebugMode() {
        toggleDebugMode(!debugMode);
    }

    private void toggleDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        environment.setDebugMode(debugMode);
        if (telemetryPanel != null) {
            telemetryPanel.setVisible(debugMode);
            telemetryPanel.setManaged(debugMode);
        }
    }

    public TelemetryPanel getTelemetryPanel() {
        return telemetryPanel;
    }
}