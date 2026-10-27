package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Food;
import edu.msoe.envsim.utilities.apputils.AgentInspectorPane;
import edu.msoe.envsim.utilities.apputils.DebugMessage;
import edu.msoe.envsim.utilities.simutils.TerrainType;
import edu.msoe.envsim.utilities.Vector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
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
import java.util.Optional;

import static javafx.scene.control.PopupControl.USE_PREF_SIZE;

public class App extends Application {
    // Styles shared with AgentInspectorPane so all floating panels look the same
    private static final String PANEL_STYLE =
            "-fx-background-color: rgba(30, 30, 30, 0.9); -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8;";
    private static final String TITLE_STYLE = "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;";
    private static final String CAPTION_STYLE = "-fx-text-fill: #aaa; -fx-font-weight: bold;";
    private static final String VALUE_STYLE = "-fx-text-fill: white;";
    private static final String BUTTON_STYLE = "-fx-background-color: #444; -fx-text-fill: white; -fx-cursor: hand;";
    private static final String SELECTED_BUTTON_STYLE = "-fx-background-color: #3b5998; -fx-text-fill: white; -fx-cursor: hand; -fx-border-color: #88c0d0; -fx-border-width: 2; -fx-border-radius: 4;";
    // Slider tick labels pull their color from this looked-up color
    private static final String SLIDER_STYLE = "-fx-text-background-color: white;";

    private Environment environment;
    private boolean debugMode = false;
    private Slider simSpeed;

    private HBox debugBox;

    // Menu pieces
    private VBox menuPanel;
    private VBox editorSection;

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
        for (int i = 0; i < 500; i++) {
            Color randomColor = Color.color(Math.random(), Math.random(), Math.random());

            new Agent(Math.random() * 30 + 3, randomColor, environment);
        }

        for (int i = 0; i < 2000; i++) {
            Vector position = Vector.random(Vector.ZERO, environment.getSize());
            new Food(position, Math.pow(Math.random(), 3) * 40 + 5, environment);
        }

        environment.startSimulation();

        gamePane.getChildren().addAll(environment, menuLayer, inspectorPane);

        setupDebugPane();

        VBox root  = new VBox();
        root.getChildren().addAll(debugBox, gamePane);

        Scene scene = new Scene(root, viewWidth, viewHeight);
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
        debugBox = new HBox();

        DebugMessage framesPerSecond = new DebugMessage(
                () -> environment.getFrameRate(), "FpS", 2, environment
        );
        DebugMessage ticksPerSecond = new DebugMessage(
                () -> environment.getSimUpdateRate(), "TpS", 2, environment
        );
        DebugMessage numAgents = new DebugMessage(
                () -> environment.getNumAgents(), "Agents", 0, environment
        );
        DebugMessage listMs = new DebugMessage(
                () -> environment.getListMs(), "listMs", 3, environment
        );
        DebugMessage decideMs = new DebugMessage(
                () -> environment.getDecideMs(), "decideMs", 3, environment
        );
        DebugMessage stepMs = new DebugMessage(
                () -> environment.getStepMs(), "stepMs", 3, environment
        );
        DebugMessage TpSpA = new DebugMessage(
                () -> 1 / (environment.getSimUpdateRate() * (double) environment.getNumAgents()), "SpAT", 10, environment
        );

        debugBox.getChildren().addAll(framesPerSecond, ticksPerSecond, numAgents, listMs, decideMs, stepMs, TpSpA);
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
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case KeyCode.HOME -> toggleDebugMode();
                case KeyCode.SPACE -> environment.toggleSimulation();
                case KeyCode.E -> environment.toggleEditMode();
                case KeyCode.R -> {
                    if (simSpeed.getValue() == 60) {
                        simSpeed.setValue(Double.MAX_VALUE);
                    } else {
                        simSpeed.setValue(60);
                    }
                }
            }
        });
    }

    private void setupEnvironment(@NotNull Stage stage, int viewWidth, int viewHeight, AgentInspectorPane inspectorPane) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double aspectRatio = screenBounds.getHeight() / screenBounds.getWidth();
        int worldWidth = (int) (7000 * 3.0);
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
        title.setStyle(TITLE_STYLE);

        Label simSpeedLabel = new Label();  // TODO: Allow the user to type in a selected tick speed
        simSpeedLabel.setStyle(VALUE_STYLE);

        simSpeed = new Slider(0, 5000, 0);
        simSpeed.setMajorTickUnit(1000);
        simSpeed.setMinorTickCount(2);
        simSpeed.setSnapToTicks(false);
        simSpeed.setShowTickLabels(true);
        simSpeed.setShowTickMarks(true);
        simSpeed.setStyle(SLIDER_STYLE);
        simSpeed.setFocusTraversable(false);
        simSpeed.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (Math.abs(newValue.doubleValue() - simSpeed.getMax()) < 0.1) {
                environment.setMaxSimUpdateRate(Double.POSITIVE_INFINITY);
                simSpeedLabel.setText("Max update rate: UNLIMITED");
            } else {
                environment.setMaxSimUpdateRate(newValue.doubleValue());
                simSpeedLabel.setText("Max update rate: " + newValue.intValue());
            }
        });
        simSpeed.setValue(simSpeed.getMax());

        Button resetCamera =  new Button("Reset Cam");
        resetCamera.setStyle(BUTTON_STYLE);
        resetCamera.setFocusTraversable(false);
        resetCamera.setOnAction(e -> environment.resetCamera(stage.getWidth()));


        // ---- Editor controls (only shown in edit mode) ----
        Label editorTitle = new Label("Terrain Editor");
        editorTitle.setStyle(TITLE_STYLE);

        Label brushTypeLabel = new Label("Brush Type");
        brushTypeLabel.setStyle(CAPTION_STYLE);

        Button grass = new Button("Grass");
        Button water = new Button("Water");
        for (Button b : new Button[]{grass, water}) {
            b.setFocusTraversable(false);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        // Initial state: Grass is selected by default
        grass.setStyle(SELECTED_BUTTON_STYLE);
        water.setStyle(BUTTON_STYLE);

        grass.setOnAction(_ -> {
            environment.setBrush(TerrainType.STANDARD);
            grass.setStyle(SELECTED_BUTTON_STYLE);
            water.setStyle(BUTTON_STYLE);
        });
        water.setOnAction(_ -> {
            environment.setBrush(TerrainType.WATER);
            water.setStyle(SELECTED_BUTTON_STYLE);
            grass.setStyle(BUTTON_STYLE);
        });
        HBox terrainOptions = new HBox(8, grass, water);

        // --- Refactored Save/Load Controls ---
        Label brushSizeLabel = new Label("Brush Size: 5");
        brushSizeLabel.setStyle(CAPTION_STYLE);

        Slider brushSize = new Slider(1, 10, 5);
        brushSize.setMajorTickUnit(1);
        brushSize.setMinorTickCount(0);
        brushSize.setSnapToTicks(true);
        brushSize.setShowTickLabels(true);
        brushSize.setShowTickMarks(true);
        brushSize.setStyle(SLIDER_STYLE);
        brushSize.setFocusTraversable(false);
        brushSize.valueProperty().addListener((observable, oldValue, newValue) -> {
            int val = newValue.intValue();
            environment.setBrushSize(val);
            brushSizeLabel.setText("Brush Size: " + val);
        });

        Label envManagementLabel = new Label("Environments");
        envManagementLabel.setStyle(CAPTION_STYLE);

        Button saveButton = new Button("Save As...");
        Button loadButton = new Button("Environment Manager");
        for (Button b : new Button[]{saveButton, loadButton}) {
            b.setStyle(BUTTON_STYLE);
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
                    e.printStackTrace();
                }
            }
        });

        loadButton.setOnAction(_ -> showEnvironmentManager(gamePane, environment));

        HBox fileIoOptions = new HBox(8, saveButton, loadButton);

        editorSection = new VBox(10,
                new Separator(),
                editorTitle,
                brushTypeLabel,
                terrainOptions,
                brushSizeLabel,
                brushSize,
                new Separator(),
                envManagementLabel,
                fileIoOptions
        );
        setEditorSectionVisible(false);

        // ---- Panel ----
        menuPanel = new VBox(10, title, simSpeedLabel, simSpeed, resetCamera, editorSection);
        menuPanel.setPadding(new Insets(15));
        menuPanel.setPrefWidth(240);
        menuPanel.setMaxWidth(USE_PREF_SIZE);
        menuPanel.setMaxHeight(USE_PREF_SIZE);
        menuPanel.setStyle(PANEL_STYLE);
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

    private void showEnvironmentManager(@NotNull StackPane gamePane, @NotNull Environment environment) {
        VBox backdrop = new VBox();
        backdrop.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75);");
        backdrop.setAlignment(Pos.CENTER);
        backdrop.setMaxWidth(Double.MAX_VALUE);
        backdrop.setMaxHeight(Double.MAX_VALUE);

        VBox dialog = new VBox(20);
        dialog.setPadding(new Insets(25));
        dialog.setStyle(PANEL_STYLE + "-fx-background-color: rgba(25, 25, 25, 0.95);");
        // Scalable with screen size but bounded to avoid over-stretching on 4K monitors
        dialog.prefWidthProperty().bind(gamePane.widthProperty().multiply(0.65));
        dialog.prefHeightProperty().bind(gamePane.heightProperty().multiply(0.70));
        dialog.setMaxWidth(950);
        dialog.setMaxHeight(700);
        dialog.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Environment Manager");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: white;");

        TilePane tilePane = new TilePane();
        tilePane.setHgap(20);
        tilePane.setVgap(20);
        tilePane.setPrefColumns(3);
        tilePane.setAlignment(Pos.TOP_CENTER);

        TextField nameField = new TextField();
        nameField.setPromptText("Enter or select environment name...");
        nameField.setStyle("-fx-control-inner-background: #333; -fx-text-fill: white; -fx-prompt-text-fill: #aaa; -fx-font-size: 14px; -fx-padding: 8;");
        nameField.setFocusTraversable(false);

        // Helper method for confirming and saving over an environment file with proper text wrapping
        java.util.function.BiConsumer<File, String> confirmAndSave = (targetFile, textName) -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Overwrite Environment");
            alert.setHeaderText("Environment Already Exists");

            // Explicitly set a wrapped content label and dialog width to prevent text truncation
            Label contentLabel = new Label("An environment named '" + textName + "' already exists. Do you want to overwrite it?");
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(380);
            alert.getDialogPane().setContent(contentLabel);
            alert.getDialogPane().setPrefWidth(450);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    environment.saveTerrain(targetFile);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        Runnable refreshThumbnails = () -> {
            tilePane.getChildren().clear();
            File dir = new File("environments");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((_, name) -> name.endsWith(".terrain"));
                if (files != null) {
                    Arrays.sort(files);
                    for (File file : files) {
                        String name = file.getName().replace(".terrain", "");
                        // Larger thumbnail dimensions for high-res/4K screens
                        WritableImage thumb = Environment.loadTerrainThumbnail(file, 200, 120);
                        ImageView imageView = new ImageView(thumb);
                        imageView.setFitWidth(200);
                        imageView.setFitHeight(120);
                        imageView.setPreserveRatio(true);

                        Label nameLabel = new Label(name);
                        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

                        Button loadCardBtn = new Button("Load");
                        loadCardBtn.setStyle(BUTTON_STYLE + "-fx-font-size: 12px;");
                        loadCardBtn.setFocusTraversable(false);
                        loadCardBtn.setOnAction(_ -> {
                            try {
                                environment.loadTerrain(file);
                                gamePane.getChildren().remove(backdrop);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });

                        Button overrideCardBtn = new Button("Override");
                        overrideCardBtn.setStyle(BUTTON_STYLE + "-fx-font-size: 12px; -fx-background-color: #5a2e2e;");
                        overrideCardBtn.setFocusTraversable(false);
                        overrideCardBtn.setOnAction(_ -> {
                            nameField.setText(name);
                            confirmAndSave.accept(file, name);
                        });

                        HBox cardActions = new HBox(8, loadCardBtn, overrideCardBtn);
                        cardActions.setAlignment(Pos.CENTER);

                        VBox card = new VBox(8, imageView, nameLabel, cardActions);
                        card.setAlignment(Pos.CENTER);
                        card.setStyle("-fx-background-color: rgba(45, 45, 45, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #555; -fx-border-radius: 8;");

                        // Selecting a card populates the name field and highlights it
                        card.setOnMouseClicked(_ -> {
                            nameField.setText(name);
                            tilePane.getChildren().forEach(n -> n.setStyle("-fx-background-color: rgba(45, 45, 45, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #555; -fx-border-radius: 8;"));
                            card.setStyle("-fx-background-color: rgba(60, 100, 140, 0.9); -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-border-color: #88c0d0; -fx-border-radius: 8; -fx-border-width: 2;");
                        });

                        tilePane.getChildren().add(card);
                    }
                }
            }
        };

        refreshThumbnails.run();

        ScrollPane scrollPane = new ScrollPane(tilePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.vbarPolicyProperty().setValue(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button saveCurrentBtn = new Button("Save / Overwrite");
        saveCurrentBtn.setStyle(BUTTON_STYLE + "-fx-font-size: 13px; -fx-padding: 8 15;");
        saveCurrentBtn.setFocusTraversable(false);
        saveCurrentBtn.setOnAction(_ -> {
            String text = nameField.getText().trim();
            if (!text.isEmpty()) {
                File targetFile = new File("environments", text + ".terrain");
                if (targetFile.exists()) {
                    confirmAndSave.accept(targetFile, text);
                } else {
                    try {
                        environment.saveTerrain(targetFile);
                        refreshThumbnails.run();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        HBox saveBox = new HBox(12, nameField, saveCurrentBtn);
        saveBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Button closeBtn = new Button("Close Manager");
        closeBtn.setStyle(BUTTON_STYLE + "-fx-font-size: 13px; -fx-padding: 8 20;");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(_ -> gamePane.getChildren().remove(backdrop));

        dialog.getChildren().addAll(title, scrollPane, new Separator(), saveBox, closeBtn);
        backdrop.getChildren().add(dialog);

        backdrop.setOnMouseClicked(e -> {
            if (e.getTarget() == backdrop) {
                gamePane.getChildren().remove(backdrop);
            }
        });

        gamePane.getChildren().add(backdrop);
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
        button.setStyle(PANEL_STYLE + "-fx-padding: 8 10 8 10; -fx-cursor: hand;");
        button.setFocusTraversable(false);
        return button;
    }

    private void setMenuOpen(boolean open) {
        menuPanel.setVisible(open);
        menuPanel.setManaged(open);
    }

    private void setEditorSectionVisible(boolean visible) {
        editorSection.setVisible(visible);
        editorSection.setManaged(visible);
    }

    private void drawRandomShapes(@NotNull GraphicsContext gc) {
        gc.setFill(Color.hsb(Math.random() * 360, 0.8, 0.9, 0.7));
        double x = Math.random() * (gc.getCanvas().getWidth() - 50);
        double y = Math.random() * (gc.getCanvas().getHeight() - 50);
        gc.fillOval(x, y, 40, 40);
    }

    private void toggleDebugMode() {
        toggleDebugMode(!debugMode);
    }

    private void toggleDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        environment.setDebugMode(debugMode);
        debugBox.setVisible(debugMode);
        debugBox.setManaged(debugMode);
    }
}