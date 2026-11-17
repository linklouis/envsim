package edu.msoe.envsim.utilities.apputils.ui;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.apputils.AppSettings;
import edu.msoe.envsim.utilities.apputils.UnitSystem;
import edu.msoe.envsim.utilities.simutils.TerrainType;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The floating control panel in the top left corner: a hamburger button that is always visible, and under it
 * a drawer with collapsible sections for the simulation, the terrain (including the terrain editor), and a
 * toggle for the {@link AgentSpawnerPane agent library}, which opens as its own wide panel beside the drawer.
 * <p>
 * This node is meant to be added to the game pane. It positions itself in the top left corner.
 * </p>
 */
public class ControlPanel extends VBox {
    private static final double DRAWER_WIDTH = 290;
    private static final double DRAWER_VERTICAL_MARGIN = 110;

    private static final double MIN_SIM_SPEED = 10.0;
    private static final double MAX_SIM_SPEED = 5000.0;
    private static final double REAL_TIME_TICKS_PER_SECOND = UnitSystem.TICKS_PER_SECOND;

    private static final int MIN_BRUSH_SIZE = 1;
    private static final int MAX_BRUSH_SIZE = 20;
    private static final int DEFAULT_BRUSH_SIZE = 5;

    private final Environment environment;
    private final StackPane gamePane;
    private final Stage stage;

    private final VBox drawer = new VBox(10);
    private final AgentSpawnerPane libraryPanel;

    private final Slider simSpeedSlider = new Slider(0, 100, 0);
    private final Label simSpeedLabel = new Label();
    private final Button pauseButton;
    private final Button unitsButton;

    private final Button editToggleButton;
    private final VBox editorTools = new VBox(10);
    private final Map<TerrainType, Button> paletteButtons = new EnumMap<>(TerrainType.class);
    private final Button squareBrushButton;
    private final Button roundBrushButton;
    private final Slider brushSizeSlider = new Slider(MIN_BRUSH_SIZE, MAX_BRUSH_SIZE, DEFAULT_BRUSH_SIZE);
    private final Label brushSizeLabel = new Label();

    private final Button libraryToggleButton;

    /**
     * @param stage          The window, used to size the panels and to anchor file dialogs
     * @param gamePane       The pane this panel sits in, which the environment manager dialog is added to
     * @param environment    The environment these controls drive
     * @param onOpenMainMenu Called when the Main Menu button is pressed
     * @param onOpenStats    Called when the Statistics button is pressed
     */
    public ControlPanel(@NotNull Stage stage, @NotNull StackPane gamePane, @NotNull Environment environment,
                        @NotNull Runnable onOpenMainMenu, @NotNull Runnable onOpenStats) {
        super(8);
        this.stage = stage;
        this.gamePane = gamePane;
        this.environment = environment;

        // Clicks on the empty space around the panels fall through to the world (painting, panning)
        setPickOnBounds(false);
        HBox panelsRow = new HBox(10);
        panelsRow.setPickOnBounds(false);
        panelsRow.setAlignment(Pos.TOP_LEFT);
        setAlignment(Pos.TOP_LEFT);
        setMaxWidth(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        StackPane.setAlignment(this, Pos.TOP_LEFT);
        StackPane.setMargin(this, new Insets(15));

        // ---- Simulation section ----
        simSpeedLabel.getStyleClass().add("value-label");
        configureSimSpeedSlider();

        pauseButton = createButton("Pause", this::togglePause);
        Button resetCameraButton = createButton("Reset Cam", () -> {
            environment.resetCamera(stage.getWidth());
            setOpen(false);
        });
        Button statsButton = createButton("Statistics  (S)", onOpenStats);
        Button mainMenuButton = createButton("Main Menu", onOpenMainMenu);
        unitsButton = createButton("", () -> AppSettings.setUnitSystem(AppSettings.getUnitSystem().next()));
        stretch(pauseButton, resetCameraButton, statsButton, mainMenuButton, unitsButton);

        HBox pauseAndCameraRow = new HBox(8, pauseButton, resetCameraButton);
        Label unitsCaption = createCaption("Units");
        HBox unitsRow = new HBox(8, unitsCaption, unitsButton);
        unitsRow.setAlignment(Pos.CENTER_LEFT);

        CollapsibleSection simulationSection = new CollapsibleSection("SIMULATION", true,
                simSpeedLabel, simSpeedSlider, pauseAndCameraRow, unitsRow, statsButton, mainMenuButton);

        // ---- Terrain section (with the editor) ----
        editToggleButton = createButton("", environment::toggleEditMode);
        stretch(editToggleButton);

        squareBrushButton = createButton("Square", () -> selectBrushShape(false));
        roundBrushButton = createButton("Round", () -> selectBrushShape(true));
        stretch(squareBrushButton, roundBrushButton);

        brushSizeLabel.getStyleClass().add("caption-label");
        configureBrushSizeSlider();

        editorTools.getChildren().addAll(
                createCaption("Terrain brush"),
                buildPalette(),
                createCaption("Brush shape"),
                new HBox(8, squareBrushButton, roundBrushButton),
                brushSizeLabel,
                brushSizeSlider
        );

        Button saveTerrainButton = createButton("Save As...", this::saveTerrainAs);
        Button manageTerrainButton = createButton("Manage...", this::openEnvironmentManager);
        stretch(saveTerrainButton, manageTerrainButton);
        HBox terrainFilesRow = new HBox(8, saveTerrainButton, manageTerrainButton);

        CollapsibleSection terrainSection = new CollapsibleSection("TERRAIN", true,
                editToggleButton, editorTools, createCaption("Terrain files"), terrainFilesRow);

        // ---- Agents section ----
        libraryPanel = new AgentSpawnerPane(environment);
        libraryToggleButton = createButton("", () -> setLibraryOpen(!isLibraryOpen()));
        stretch(libraryToggleButton);
        CollapsibleSection agentsSection = new CollapsibleSection("AGENTS", true, libraryToggleButton);

        // ---- Drawer ----
        Label drawerTitle = new Label("Menu");
        drawerTitle.getStyleClass().add("title-label");

        VBox sections = new VBox(8, simulationSection, terrainSection, agentsSection);
        ScrollPane sectionsScrollPane = new ScrollPane(sections);
        sectionsScrollPane.getStyleClass().add("transparent-scroll-pane");
        sectionsScrollPane.setFitToWidth(true);
        sectionsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(sectionsScrollPane, Priority.ALWAYS);

        drawer.getChildren().addAll(drawerTitle, sectionsScrollPane);
        drawer.getStyleClass().add("panel");
        drawer.setPadding(new Insets(12));
        drawer.setPrefWidth(DRAWER_WIDTH);
        drawer.setMinWidth(DRAWER_WIDTH);
        drawer.setMaxWidth(DRAWER_WIDTH);
        drawer.maxHeightProperty().bind(stage.heightProperty().subtract(DRAWER_VERTICAL_MARGIN));
        drawer.setFocusTraversable(false);

        libraryPanel.maxHeightProperty().bind(stage.heightProperty().subtract(DRAWER_VERTICAL_MARGIN));
        libraryPanel.setVisible(false);
        libraryPanel.setManaged(false);

        panelsRow.getChildren().addAll(drawer, libraryPanel);

        Button hamburgerButton = createHamburgerButton();
        hamburgerButton.setOnAction(_ -> setOpen(!isOpen()));
        getChildren().addAll(hamburgerButton, panelsRow);

        // ---- Initial state ----
        selectTerrain(TerrainType.STANDARD);
        selectBrushShape(false);
        updateBrushSizeLabel();
        updateUnitsButton();
        onModeChanged(false);
        updateLibraryToggleButton();
        setOpen(false);

        AppSettings.unitSystemProperty().addListener((_, _, _) -> {
            updateUnitsButton();
            updateBrushSizeLabel();
        });

        // Keeps the pause button honest when the simulation is paused with the keyboard instead
        Timeline stateRefresher = new Timeline(new KeyFrame(Duration.millis(250), _ -> refreshStateDisplays()));
        stateRefresher.setCycleCount(Animation.INDEFINITE);
        stateRefresher.play();
    }


    /*
     * Opening and closing:
     */

    public boolean isOpen() {
        return drawer.isVisible();
    }

    public void setOpen(boolean open) {
        drawer.setVisible(open);
        drawer.setManaged(open);
        if (!open) {
            setLibraryOpen(false);
        } else {
            refreshStateDisplays();
        }
    }

    private boolean isLibraryOpen() {
        return libraryPanel.isVisible();
    }

    private void setLibraryOpen(boolean open) {
        libraryPanel.setVisible(open);
        libraryPanel.setManaged(open);
        if (open) {
            libraryPanel.refresh();
        }
        updateLibraryToggleButton();
    }

    private void updateLibraryToggleButton() {
        libraryToggleButton.setText(isLibraryOpen() ? "Hide Agent Library" : "Open Agent Library");
    }

    /**
     * @return Whether the world is in terrain edit mode. While it is, the panel stays open so the tools stay reachable.
     */
    public boolean isEditing() {
        return environment.isEditMode();
    }

    /**
     * Call when the environment switches between edit mode and simulation mode, from any cause (the button or the hotkey).
     * Shows or hides the editor tools, and opens the panel when entering edit mode.
     */
    public void onModeChanged(boolean editing) {
        editorTools.setVisible(editing);
        editorTools.setManaged(editing);

        editToggleButton.setText(editing ? "Back to Simulation  (E)" : "Edit Terrain  (E)");
        editToggleButton.getStyleClass().removeAll("sim-button", "selected-button");
        editToggleButton.getStyleClass().add(editing ? "selected-button" : "sim-button");

        if (editing) {
            setOpen(true);
        }
        refreshStateDisplays();
    }

    private void refreshStateDisplays() {
        if (!isOpen()) return;
        pauseButton.setText(environment.isSimulationRunning() ? "Pause" : "Resume");
    }

    private void togglePause() {
        environment.toggleSimulation();
        refreshStateDisplays();
    }


    /*
     * Simulation speed:
     */

    private void configureSimSpeedSlider() {
        simSpeedSlider.setMajorTickUnit(25);
        simSpeedSlider.setMinorTickCount(4);
        simSpeedSlider.setSnapToTicks(false);
        simSpeedSlider.setShowTickLabels(true);
        simSpeedSlider.setShowTickMarks(true);
        simSpeedSlider.getStyleClass().add("slider-tick-fix");
        simSpeedSlider.setFocusTraversable(false);

        // Map slider values (0-100) to actual speeds for the tick labels
        simSpeedSlider.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                if (value == null) return "";
                if (value >= 99.9) return "Max";
                double rate = sliderValueToRate(value);
                if (rate >= 1000) {
                    return String.format(Locale.US, "%.1fk", rate / 1000.0);
                }
                return String.format(Locale.US, "%d", (int) rate);
            }

            @Override
            public Double fromString(String string) {
                return 0.0; // Not needed for read-only labels
            }
        });

        simSpeedSlider.valueProperty().addListener((_, _, newValue) -> {
            double value = newValue.doubleValue();
            if (value >= 99.9) {
                environment.setMaxSimUpdateRate(Double.POSITIVE_INFINITY);
                simSpeedLabel.setText("Max update rate: UNLIMITED");
            } else {
                double rate = sliderValueToRate(value);
                environment.setMaxSimUpdateRate(rate);
                simSpeedLabel.setText("Max update rate: " + String.format(Locale.US, "%,d", (int) rate));
            }
        });
        simSpeedSlider.setValue(simSpeedSlider.getMax()); // Defaults to UNLIMITED
    }

    private static double sliderValueToRate(double sliderValue) {
        return MIN_SIM_SPEED * Math.pow(MAX_SIM_SPEED / MIN_SIM_SPEED, sliderValue / 100.0);
    }

    /**
     * Flips the simulation speed between real time (60 ticks per second) and unlimited.
     */
    public void toggleSpeedPreset() {
        double realTimeSliderValue = 100.0 * Math.log10(REAL_TIME_TICKS_PER_SECOND / MIN_SIM_SPEED)
                / Math.log10(MAX_SIM_SPEED / MIN_SIM_SPEED);
        boolean isCurrentlyRealTime = Math.abs(simSpeedSlider.getValue() - realTimeSliderValue) < 1.0;
        simSpeedSlider.setValue(isCurrentlyRealTime ? 100.0 : realTimeSliderValue);
    }

    private void updateUnitsButton() {
        unitsButton.setText(AppSettings.getUnitSystem().getDisplayName());
    }


    /*
     * Terrain editor:
     */

    private @NotNull GridPane buildPalette() {
        GridPane palette = new GridPane();
        palette.setHgap(8);
        palette.setVgap(8);
        for (int column = 0; column < 2; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(50);
            palette.getColumnConstraints().add(constraints);
        }

        TerrainType[] terrainTypes = TerrainType.values();
        for (int index = 0; index < terrainTypes.length; index++) {
            TerrainType terrainType = terrainTypes[index];
            Button paletteButton = createPaletteButton(terrainType, index + 1);
            paletteButtons.put(terrainType, paletteButton);
            palette.add(paletteButton, index % 2, index / 2);
        }
        return palette;
    }

    private @NotNull Button createPaletteButton(@NotNull TerrainType terrainType, int hotkeyNumber) {
        Region swatch = new Region();
        swatch.setMinSize(14, 14);
        swatch.setPrefSize(14, 14);
        swatch.setMaxSize(14, 14);
        swatch.setBackground(new Background(new BackgroundFill(terrainType.getColor(), new CornerRadii(3), Insets.EMPTY)));
        swatch.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 3; -fx-border-width: 0.75;");

        Label nameLabel = new Label(displayNameOf(terrainType));
        nameLabel.getStyleClass().add("value-label");

        Label hotkeyLabel = new Label(String.valueOf(hotkeyNumber));
        hotkeyLabel.getStyleClass().add("caption-label");
        hotkeyLabel.setStyle("-fx-font-size: 10px;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox content = new HBox(6, swatch, nameLabel, spacer, hotkeyLabel);
        content.setAlignment(Pos.CENTER_LEFT);

        Button button = createButton("", () -> selectTerrain(terrainType));
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private static @NotNull String displayNameOf(@NotNull TerrainType terrainType) {
        return switch (terrainType) {
            case DESERT -> "Desert";
            case STANDARD -> "Grass";
            case MARSH -> "Marsh";
            case WATER -> "Water";
        };
    }

    /**
     * Picks the terrain the brush paints.
     */
    public void selectTerrain(@NotNull TerrainType terrainType) {
        environment.setBrush(terrainType);
        for (Map.Entry<TerrainType, Button> entry : paletteButtons.entrySet()) {
            setSelectedStyle(entry.getValue(), entry.getKey() == terrainType);
        }
    }

    private void selectBrushShape(boolean round) {
        environment.setBrushRound(round);
        setSelectedStyle(squareBrushButton, !round);
        setSelectedStyle(roundBrushButton, round);
    }

    private void configureBrushSizeSlider() {
        brushSizeSlider.setMajorTickUnit(1);
        brushSizeSlider.setMinorTickCount(0);
        brushSizeSlider.setSnapToTicks(true);
        brushSizeSlider.setShowTickMarks(true);
        brushSizeSlider.getStyleClass().add("slider-tick-fix");
        brushSizeSlider.setFocusTraversable(false);
        brushSizeSlider.valueProperty().addListener((_, _, newValue) -> {
            environment.setBrushSize(newValue.intValue());
            updateBrushSizeLabel();
        });
        environment.setBrushSize(DEFAULT_BRUSH_SIZE);
    }

    /**
     * Makes the brush bigger or smaller by {@code delta} steps. Used by the bracket-key hotkeys.
     */
    public void adjustBrushSize(int delta) {
        brushSizeSlider.setValue(Math.clamp(brushSizeSlider.getValue() + delta, MIN_BRUSH_SIZE, MAX_BRUSH_SIZE));
    }

    private void updateBrushSizeLabel() {
        int brushRadiusInTiles = (int) brushSizeSlider.getValue();
        int brushDiameterInTiles = 2 * brushRadiusInTiles + 1;
        double brushDiameterInPixels = (double) brushDiameterInTiles * Environment.TERRAIN_RESOLUTION_RATIO;
        brushSizeLabel.setText("Brush size: " + brushDiameterInTiles + " tiles  ("
                + AppSettings.getUnitSystem().formatLength(brushDiameterInPixels) + " wide)");
    }


    /*
     * Terrain files:
     */

    private void saveTerrainAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Terrain Background");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Terrain Files (*.terrain)", "*.terrain"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                environment.saveTerrain(file);
            } catch (Exception exception) {
                showError("Save Failed", "Could not save terrain to file.", exception.getMessage());
            }
        }
    }

    private void openEnvironmentManager() {
        EnvironmentManagerDialog managerDialog = new EnvironmentManagerDialog(gamePane, environment);
        gamePane.getChildren().add(managerDialog);
    }

    private static void showError(@NotNull String title, @NotNull String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);

        Label contentLabel = new Label(message != null ? message : "An unexpected error occurred.");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(380);
        alert.getDialogPane().setContent(contentLabel);
        alert.getDialogPane().setPrefWidth(450);
        alert.showAndWait();
    }


    /*
     * Building blocks:
     */

    private static @NotNull Button createButton(@NotNull String text, @NotNull Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("sim-button");
        button.setFocusTraversable(false);
        button.setOnAction(_ -> action.run());
        return button;
    }

    private static @NotNull Label createCaption(@NotNull String text) {
        Label label = new Label(text);
        label.getStyleClass().add("caption-label");
        return label;
    }

    /**
     * Lets buttons in a row share the row's width evenly.
     */
    private static void stretch(@NotNull Button @NotNull ... buttons) {
        for (Button button : buttons) {
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
    }

    private static void setSelectedStyle(@NotNull Button button, boolean selected) {
        button.getStyleClass().removeAll("sim-button", "selected-button");
        button.getStyleClass().add(selected ? "selected-button" : "sim-button");
    }

    /**
     * A button showing three horizontal bars.
     */
    private static @NotNull Button createHamburgerButton() {
        VBox bars = new VBox(4);
        bars.setAlignment(Pos.CENTER);
        for (int barIndex = 0; barIndex < 3; barIndex++) {
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
        button.setStyle("-fx-padding: 8 10 8 10; -fx-cursor: hand;");
        button.setFocusTraversable(false);
        return button;
    }
}