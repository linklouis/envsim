package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Food;
import edu.msoe.envsim.utilities.apputils.AgentInspectorPane;
import edu.msoe.envsim.utilities.apputils.EnvironmentManagerDialog;
import edu.msoe.envsim.utilities.apputils.TelemetryPanel;
import edu.msoe.envsim.utilities.simutils.AgentData;
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

    private static double MIN_SIM_SPEED = 10.0;
    private static double MAX_SIM_SPEED = 5000.0;

    private Environment environment;
    private boolean debugMode = false;
    private Slider simSpeed;

    private TelemetryPanel telemetryPanel;

    // Menu pieces
    private VBox menuPanel;
    private VBox editorSection;
    private VBox agentSection;
    private Button toggleAgentMgrBtn;
    private TilePane agentTilePane;
    private Runnable refreshAgentCards;

    @Override
    public void start(@NotNull Stage stage) {
        int viewWidth = 1080;
        int viewHeight = 720;

        AgentInspectorPane inspectorPane = new AgentInspectorPane(() -> environment.clearSelectedAgent());
        StackPane.setAlignment(inspectorPane, Pos.TOP_RIGHT);
        StackPane.setMargin(inspectorPane, new Insets(15));

        // Environment must exist before the menu is built, since the sim speed slider pushes its value into it
        setupEnvironment(stage, viewWidth, viewHeight, inspectorPane);

        // Layer Controls over Canvas using StackPane
        StackPane gamePane = new StackPane();
        VBox menuLayer = setupMenu(stage, gamePane);

        // Populate Entities
        for (int i = 0; i < 12000; i++) {
            Color randomColor = Color.color(Math.random(), Math.random(), Math.random());

            new Agent(Math.random() * 30 + 3, randomColor, environment);
        }

        for (int i = 0; i < 9000; i++) {
            Vector position = Vector.random(Vector.ZERO, environment.getSize());
            new Food(position, Math.pow(Math.random(), 4) * 40 + 5, environment);
        }

        environment.startSimulation();

        gamePane.getChildren().addAll(environment, menuLayer, inspectorPane);

        setupDebugPane();

        VBox root  = new VBox();
        root.getChildren().addAll(telemetryPanel, gamePane);

        Scene scene = new Scene(root, viewWidth, viewHeight);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        stage.setScene(scene);
        stage.setTitle("EnvSim");
        stage.setOnCloseRequest(_ -> {
            Platform.exit();
            System.exit(0);
        });
        stage.show();

        setupKeyboardInputs(scene, environment);
        setupClickAwayClose(scene, menuLayer, inspectorPane);
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
     * In edit mode the menu stays open, since clicking the canvas there paints terrain and the editor
     * controls need to stay reachable.
     */
    private void setupClickAwayClose(@NotNull Scene scene, @NotNull Node menuLayer, @NotNull Node inspectorPane) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!menuPanel.isVisible() || editorSection.isVisible()) return;

            Object target = event.getTarget();
            if (target instanceof Node node
                    && !isWithin(node, menuLayer)
                    && !isWithin(node, inspectorPane)) {
                setMenuOpen(false);
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
                    // Calculate the slider position that maps precisely to 60 TpS
                    double targetValFor60 = 100.0 * Math.log10(60.0 / MIN_SIM_SPEED) / Math.log10(MAX_SIM_SPEED / MIN_SIM_SPEED);
                    boolean isCurrently60 = Math.abs(simSpeed.getValue() - targetValFor60) < 1.0;

                    if (isCurrently60) {
                        simSpeed.setValue(100.0); // Switch to UNLIMITED
                    } else {
                        simSpeed.setValue(targetValFor60); // Switch to 60 TpS
                    }
                    event.consume();
                }
            }
        });
    }

    private void setupEnvironment(@NotNull Stage stage, int viewWidth, int viewHeight, AgentInspectorPane inspectorPane) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double aspectRatio = screenBounds.getHeight() / screenBounds.getWidth();
        int worldWidth = (int) (7000 * 9.0);
        int worldHeight = (int) (worldWidth * aspectRatio);
        double zoom = screenBounds.getWidth() / worldWidth;

        Environment.createDefaultEnvironments(worldWidth, worldHeight);

        // Leaving edit mode hides the editor controls; entering it shows them and pops the menu open
        Runnable onSimModeStart = () -> setEditorSectionVisible(false);
        Runnable onEditModeStart = () -> {
            setEditorSectionVisible(true);
            setMenuOpen(true);
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

    /**
     * Builds the floating menu: a hamburger button that is always visible, and a panel underneath it
     * holding the sim controls plus (in edit mode only) the editor controls.
     *
     * @return the layer to add on top of the environment
     */
    private @NotNull VBox setupMenu(Stage stage, StackPane gamePane) {
        // ---- Simulation controls (always in the menu) ----
        Label title = new Label("Menu");
        title.getStyleClass().add("title-label");

        Label simSpeedLabel = new Label();  // TODO: Allow the user to type in a selected tick speed
        simSpeedLabel.getStyleClass().add("value-label");

        simSpeed = new Slider(0, 100, 0);
        simSpeed.setMajorTickUnit(25);
        simSpeed.setMinorTickCount(4);
        simSpeed.setSnapToTicks(false);
        simSpeed.setShowTickLabels(true);
        simSpeed.setShowTickMarks(true);
        simSpeed.getStyleClass().add("slider-tick-fix");
        simSpeed.setFocusTraversable(false);

        // Map slider values (0-100) to actual speeds for the tick labels
        simSpeed.setLabelFormatter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Double value) {
                if (value == null) return "";
                if (value >= 99.9) return "Max";
                double rate = MIN_SIM_SPEED * Math.pow(MAX_SIM_SPEED / MIN_SIM_SPEED, value / 100.0);
                if (rate >= 1000) {
                    return String.format(java.util.Locale.US, "%.1fk", rate / 1000.0);
                }
                return String.format(java.util.Locale.US, "%d", (int) rate);
            }

            @Override
            public Double fromString(String string) {
                return 0.0; // Not needed for read-only labels
            }
        });
        simSpeed.valueProperty().addListener((observable, oldValue, newValue) -> {
            double val = newValue.doubleValue();
            if (val >= 99.9) {
                environment.setMaxSimUpdateRate(Double.POSITIVE_INFINITY);
                simSpeedLabel.setText("Max update rate: UNLIMITED");
            } else {
                double rate = MIN_SIM_SPEED * Math.pow(MAX_SIM_SPEED / MIN_SIM_SPEED, val / 100.0);
                environment.setMaxSimUpdateRate(rate);
                simSpeedLabel.setText("Max update rate: " + String.format(java.util.Locale.US, "%,d", (int) rate));
            }
        });
        simSpeed.setValue(simSpeed.getMax()); // Defaults to UNLIMITED

        // Custom logarithmic tick labels matching the 0-100 scale distribution
        HBox speedLabels = new HBox();
        speedLabels.setAlignment(Pos.CENTER_LEFT);
        String[] labelsText = {"10", "100", "1k", "5k", "Max"};
        for (int i = 0; i < labelsText.length; i++) {
            Label lbl = new Label(labelsText[i]);
            lbl.setStyle("-fx-text-fill: #aaa; -fx-font-size: 9px;");
            if (i == 0) {
                lbl.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(lbl, Priority.NEVER);
            } else if (i == labelsText.length - 1) {
                lbl.setAlignment(Pos.CENTER_RIGHT);
                HBox.setHgrow(lbl, Priority.NEVER);
            } else {
                lbl.setAlignment(Pos.CENTER);
                HBox.setHgrow(lbl, Priority.ALWAYS);
            }
            speedLabels.getChildren().add(lbl);
        }

        Button resetCamera =  new Button("Reset Cam");
        resetCamera.getStyleClass().add("sim-button");
        resetCamera.setFocusTraversable(false);
        resetCamera.setOnAction(_ -> {
            environment.resetCamera(stage.getWidth());
            setMenuOpen(false);
        });

        // --- File management controls (always in the menu) ---
        Label envManagementLabel = new Label("Environments");
        envManagementLabel.getStyleClass().add("caption-label");

        Button saveButton = new Button("Save As...");
        Button loadButton = new Button("Environment Manager");
        for (Button b : new Button[]{saveButton, loadButton}) {
            b.getStyleClass().add("sim-button"); // If using style.css
            b.setFocusTraversable(false);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        // TODO: If saving over an already existing file, do confirmation popup
        // TODO: Allow user to set default terrain file, or just load the most recently selected one
        // TODO: Have some base terrain that you can reset to with just one button click (Or like a few templates you can select through a menu)
        // TODO: Have all saved terrains be accessable through a menu rather than making the user manage the files
        saveButton.setOnAction(_ -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Terrain Background");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Terrain Files (*.terrain)", "*.terrain"));
            File file = fileChooser.showSaveDialog(environment.getScene().getWindow());
            if (file != null) {
                try {
                    environment.saveTerrain(file);
                } catch (Exception e) {
                    showErrorPopup("Save Failed", "Could not save terrain to file.", e.getMessage());
                }
            }
        });

        loadButton.setOnAction(_ -> {
            EnvironmentManagerDialog managerDialog = new EnvironmentManagerDialog(gamePane, environment);
            gamePane.getChildren().add(managerDialog);
        });

        HBox fileIoOptions = new HBox(8, saveButton, loadButton);


        // ---- Editor controls (only shown in edit mode) ----
        Label editorTitle = new Label("Terrain Editor");
        editorTitle.getStyleClass().add("title-label");

        Label brushTypeLabel = new Label("Brush Type");
        brushTypeLabel.getStyleClass().add("caption-label");

        Button desert = new Button("Desert");
        Button grass = new Button("Grass");
        Button marsh = new Button("Marsh");
        Button water = new Button("Water");
        for (Button b : new Button[]{desert, grass, marsh, water}) {
            b.setFocusTraversable(false);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        // Initial state: Grass is selected by default
        desert.getStyleClass().add("sim-button");
        grass.getStyleClass().add("selected-button");
        marsh.getStyleClass().add("sim-button");
        water.getStyleClass().add("sim-button");


        // Helper to update brush button styles cleanly
        Runnable updateBrushStyles = () -> {
            desert.getStyleClass().removeAll("selected-button");
            grass.getStyleClass().removeAll("selected-button");
            marsh.getStyleClass().removeAll("selected-button");
            water.getStyleClass().removeAll("selected-button");
        };

        desert.setOnAction(_ -> {
            environment.setBrush(TerrainType.DESERT);
            updateBrushStyles.run();
            desert.getStyleClass().add("selected-button");
        });
        grass.setOnAction(_ -> {
            environment.setBrush(TerrainType.STANDARD);
            updateBrushStyles.run();
            grass.getStyleClass().add("selected-button");
        });
        marsh.setOnAction(_ -> {
            environment.setBrush(TerrainType.MARSH);
            updateBrushStyles.run();
            marsh.getStyleClass().add("selected-button");
        });
        water.setOnAction(_ -> {
            environment.setBrush(TerrainType.WATER);
            updateBrushStyles.run();
            water.getStyleClass().add("selected-button");
        });
        HBox terrainOptions = new HBox(8, desert, grass, marsh, water);

        Label brushSizeLabel = new Label("Brush Size: 5");
        brushSizeLabel.getStyleClass().add("caption-label");

        Slider brushSize = new Slider(1, 20, 5);
        brushSize.setMajorTickUnit(1);
        brushSize.setMinorTickCount(0);
        brushSize.setSnapToTicks(true);
        brushSize.setShowTickLabels(true);
        brushSize.setShowTickMarks(true);
        brushSize.getStyleClass().add("slider-tick-fix");
        brushSize.setFocusTraversable(false);
        brushSize.valueProperty().addListener((observable, oldValue, newValue) -> {
            int val = newValue.intValue();
            environment.setBrushSize(val);
            brushSizeLabel.setText("Brush Size: " + val);
        });

        editorSection = new VBox(10,
                new Separator(),
                editorTitle,
                brushTypeLabel,
                terrainOptions,
                brushSizeLabel,
                brushSize
        );
        setEditorSectionVisible(false);

        // --- Agent Manager Section (Appends to the bottom of the menu) ---
        Label agentMenuLabel = new Label("Agents");
        agentMenuLabel.getStyleClass().add("slider-tick-fix");

        toggleAgentMgrBtn = new Button("Show Saved Agents");
        toggleAgentMgrBtn.getStyleClass().add("sim-button");
        toggleAgentMgrBtn.setFocusTraversable(false);
        toggleAgentMgrBtn.setMaxWidth(Double.MAX_VALUE);

        agentTilePane = new TilePane();
        agentTilePane.setHgap(6);
        agentTilePane.setVgap(6);
        agentTilePane.setPrefColumns(1);
        agentTilePane.setAlignment(Pos.TOP_CENTER);

        refreshAgentCards = () -> {
            agentTilePane.getChildren().clear();
            File dir = new File("agents");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((_, name) -> name.endsWith(".agent"));
                if (files != null) {
                    Arrays.sort(files);
                    for (File file : files) {
                        String name = file.getName().replace(".agent", "");

                        // Compact thumbnail sized to fit within menu width cleanly
                        WritableImage thumb = loadAgentThumbnail(file, 90, 45);
                        ImageView imageView = new ImageView(thumb);
                        imageView.setFitWidth(90);
                        imageView.setFitHeight(45);
                        imageView.setPreserveRatio(true);

                        VBox cardDetails = makeSavedAgentCardDetails(file, name);
                        HBox card = makeSavedAgentCard(file, imageView, cardDetails);

                        agentTilePane.getChildren().add(card);
                    }
                }
            }
        };

        refreshAgentCards.run();

        ScrollPane agentScrollPane = new ScrollPane(agentTilePane);
        agentScrollPane.setFitToWidth(true);
        agentScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        agentScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Prevent horizontal scrollbars
        agentScrollPane.vbarPolicyProperty().setValue(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        agentScrollPane.setPrefHeight(160); // Default preferred height when expanded
        VBox.setVgrow(agentScrollPane, Priority.ALWAYS);

        agentSection = new VBox(6, agentScrollPane);
        agentSection.setVisible(false);
        agentSection.setManaged(false);
        VBox.setVgrow(agentSection, Priority.ALWAYS);

        toggleAgentMgrBtn.setOnAction(_ -> {
            boolean show = !agentSection.isVisible();
            agentSection.setVisible(show);
            agentSection.setManaged(show);
            toggleAgentMgrBtn.setText(show ? "Hide Saved Agents" : "Show Saved Agents");
            if (show && refreshAgentCards != null) {
                refreshAgentCards.run();
            }
        });

        VBox agentMenuEntry = new VBox(8, new Separator(), agentMenuLabel, toggleAgentMgrBtn);

        // ---- Panel ----
        menuPanel = new VBox(10, title, simSpeedLabel, simSpeed, resetCamera, new Separator(), envManagementLabel, fileIoOptions, agentMenuEntry, agentSection, editorSection);
        menuPanel.setPadding(new Insets(15));
        menuPanel.setPrefWidth(240);
        menuPanel.setMaxWidth(USE_PREF_SIZE);
        menuPanel.setMaxHeight(USE_PREF_SIZE);
        // Limit maximum height to the window height so it stays onscreen and scrolls when expanded
        menuPanel.maxHeightProperty().bind(stage.heightProperty().subtract(40));
        menuPanel.getStyleClass().add("panel");
        menuPanel.setFocusTraversable(false);
        setMenuOpen(false);

        // ---- Hamburger button + layer ----
        Button hamburger = createHamburgerButton();
        hamburger.setOnAction(_ -> setMenuOpen(!menuPanel.isVisible()));

        VBox menuLayer = new VBox(8, hamburger, menuPanel);
        menuLayer.setAlignment(Pos.TOP_LEFT);
        menuLayer.setMaxWidth(USE_PREF_SIZE);
        menuLayer.setMaxHeight(USE_PREF_SIZE);
        // Let clicks on empty space around the menu fall through to the environment (painting/panning)
        menuLayer.setPickOnBounds(false);

        // Align the floating menu to the top-left corner
        StackPane.setAlignment(menuLayer, Pos.TOP_LEFT);
        StackPane.setMargin(menuLayer, new Insets(15));

        return menuLayer;
    }

    private static @NotNull HBox makeSavedAgentCard(File file, ImageView imageView, VBox cardDetails) {
        HBox card = new HBox(8, imageView, cardDetails);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: rgba(45, 45, 45, 0.9); -fx-background-radius: 6; -fx-padding: 5 8; -fx-cursor: hand; -fx-border-color: #555; -fx-border-radius: 6;");
        card.setMaxWidth(Double.MAX_VALUE);

        card.setOnDragDetected(event -> {
            javafx.scene.input.Dragboard db = card.startDragAndDrop(javafx.scene.input.TransferMode.COPY);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(file.getAbsolutePath());
            db.setContent(content);
            event.consume();
        });
        return card;
    }

    private @NotNull VBox makeSavedAgentCardDetails(File file, String name) {
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
        nameLabel.setMaxWidth(100);

        Button spawnBtn = new Button("Spawn Randomly");
        spawnBtn.getStyleClass().add("sim-button");
        spawnBtn.setStyle("-fx-font-size: 9px; -fx-padding: 3 6;");
        spawnBtn.setFocusTraversable(false);
        spawnBtn.setOnAction(_ -> CompletableFuture.supplyAsync(() -> {
            try {
                return AgentData.loadFromFile(file);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).thenAcceptAsync(data -> {
            try {
                Vector randomPos = Vector.random(Vector.ZERO, environment.getSize());
                environment.spawnSelectedAgent(data, randomPos);
            } catch (Exception ex) {
                showErrorPopup("Spawn Failed", "Could not spawn agent.", ex.getMessage());
            }
        }, Platform::runLater));

        VBox cardDetails = new VBox(2, nameLabel, spawnBtn);
        cardDetails.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cardDetails, Priority.ALWAYS);
        return cardDetails;
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

    /**
     * A button showing three horizontal bars.
     */
    private @NotNull Button createHamburgerButton() {
        VBox bars = new VBox(4);
        bars.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            Region bar = new Region();
            bar.setMinSize(20, 3);
            bar.setPrefSize(20, 3);
            bar.setMaxSize(20, 3);
            bar.setStyle("-fx-background-color: white; -fx-background-radius: 2;");
            bars.getChildren().add(bar);
        }

        Button button = new Button();
        button.setGraphic(bars);
        button.getStyleClass().add("sim-button");
        button.setStyle("-fx-padding: 8 10 8 10; -fx-cursor: hand;"); // Keep layout-specific padding here
        button.setFocusTraversable(false);
        return button;
    }

    private void setMenuOpen(boolean open) {
        menuPanel.setVisible(open);
        menuPanel.setManaged(open);
        if (!open && agentSection != null) {
            agentSection.setVisible(false);
            agentSection.setManaged(false);
            if (toggleAgentMgrBtn != null) {
                toggleAgentMgrBtn.setText("Show Saved Agents");
            }
        }
    }

    private void setEditorSectionVisible(boolean visible) {
        editorSection.setVisible(visible);
        editorSection.setManaged(visible);
    }

    private void toggleDebugMode() {
        toggleDebugMode(!debugMode);
    }

    private void toggleDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        environment.setDebugMode(debugMode);
        telemetryPanel.setVisible(debugMode);
        telemetryPanel.setManaged(debugMode);
    }

    private @NotNull WritableImage loadAgentThumbnail(File file, int thumbW, int thumbH) {
        try {
            AgentData data = AgentData.loadFromFile(file);
            WritableImage img = new WritableImage(thumbW, thumbH);
            Canvas canvas = new Canvas(thumbW, thumbH);
            GraphicsContext gc = canvas.getGraphicsContext2D();

            gc.setFill(Color.web("#1e1e1e"));
            gc.fillRect(0, 0, thumbW, thumbH);

            double cx = thumbW / 2.0;
            double cy = thumbH / 2.0;
            double radius = Math.clamp(data.size(), 3.0, Math.min(thumbW, thumbH) * 0.35);

            gc.setFill(data.color() != null ? data.color() : Color.WHITE);
            gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);

            return canvas.snapshot(null, img);
        } catch (Exception e) {
            WritableImage fallback = new WritableImage(thumbW, thumbH);
            PixelWriter writer = fallback.getPixelWriter();
            for (int x = 0; x < thumbW; x++) {
                for (int y = 0; y < thumbH; y++) {
                    writer.setColor(x, y, Color.DARKGRAY);
                }
            }
            return fallback;
        }
    }

    public TelemetryPanel getTelemetryPanel() {
        return telemetryPanel;
    }
}