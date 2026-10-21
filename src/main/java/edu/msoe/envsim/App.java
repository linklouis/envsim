package edu.msoe.envsim;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Food;
import edu.msoe.envsim.utilities.DebugMessage;
import edu.msoe.envsim.utilities.TerrainType;
import edu.msoe.envsim.utilities.Vector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class App extends Application {
    private Environment environment;
    private boolean debugMode = true;
    private Slider simSpeed;

    private HBox debugBox;

    @Override
    public void start(@NotNull Stage stage) {
        int viewWidth = 1080;
        int viewHeight = 720;
        TitledPane collapsibleMenu = new TitledPane();

        setupEnvironment(stage,  viewWidth, viewHeight, collapsibleMenu);
        setupMenu(collapsibleMenu);

        // Populate Entities
        for (int i = 0; i < 500; i++) {
            Color randomColor = Color.color(Math.random(), Math.random(), Math.random());

            new Agent(Math.random() * 20 + 3, randomColor, environment);
        }

        for (int i = 0; i < 2000; i++) {
            Vector position = Vector.random(Vector.zero, environment.getSize());
            new Food(position, Math.pow(Math.random(), 3) * 40 + 5, environment);
        }

        environment.startSimulation();

        // Layer Controls over Canvas using StackPane
        StackPane gamePane = new StackPane();
        gamePane.getChildren().addAll(environment, collapsibleMenu);

        debugBox = new HBox();
        DebugMessage framesPerSecond = new DebugMessage(
                () -> environment.getFrameRate() + " FpS",
                environment
        );
        DebugMessage ticksPerSecond = new DebugMessage(
                () -> environment.getSimUpdateRate() + " TpS",
                environment
        );
        DebugMessage numAgents = new DebugMessage(
                () -> environment.getNumAgents() + " Agents",
                environment
        );
        debugBox.getChildren().addAll(framesPerSecond, ticksPerSecond, numAgents);

        toggleDebugMode(debugMode);


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

    private void setupEnvironment(@NotNull Stage stage, int viewWidth, int viewHeight, TitledPane editorMenu) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double aspectRatio = screenBounds.getHeight() / screenBounds.getWidth();
        int worldWidth = (int) (7000 * 3.0);
        int worldHeight = (int) (worldWidth * aspectRatio);
        double zoom = screenBounds.getWidth() / worldWidth;

        Runnable onSimModeStart = () -> editorMenu.visibleProperty().set(false);
        Runnable onEditModeStart = () -> editorMenu.visibleProperty().set(true);

        environment = new Environment(viewWidth, viewHeight, worldWidth, worldHeight, zoom, debugMode, onSimModeStart, onEditModeStart);

        environment.widthProperty().bind(stage.widthProperty());
        environment.heightProperty().bind(stage.heightProperty());
    }

    /**
     * Creats a temp menu
     * @return
     */
    private void setupMenu(@NotNull TitledPane collapsibleMenu) {
        VBox menuContent = new VBox(10);
        menuContent.setPadding(new Insets(10));
        menuContent.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9);");

        HBox terrainOptions = new HBox();
        Button grass = new Button("Grass");
        Button water = new Button("Water");
        grass.setOnAction(_ -> environment.setBrush(TerrainType.STANDARD));
        water.setOnAction(_ -> environment.setBrush(TerrainType.WATER));
        terrainOptions.getChildren().addAll(grass, water);

        Slider brushSize = new Slider(1, 10, 5);
        brushSize.setMajorTickUnit(1);
        brushSize.setMinorTickCount(0);
        brushSize.setSnapToTicks(true);
        brushSize.setShowTickLabels(true);
        brushSize.setShowTickMarks(true);
        brushSize.valueProperty().addListener((observable, oldValue, newValue) -> {
            environment.setBrushSize(newValue.intValue());
        });

        Label brushSizeLabel = new Label("Brush size");


        Label simSpeedLabel = new Label();

        simSpeed = new Slider(0, 5000, 0);
        simSpeed.setMajorTickUnit(1000);
        simSpeed.setMinorTickCount(2);
        simSpeed.setSnapToTicks(false);
        simSpeed.setShowTickLabels(true);
        simSpeed.setShowTickMarks(true);
        simSpeed.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (Math.abs(newValue.doubleValue() - simSpeed.getMax()) < 0.1) {
                environment.setMaxSimUpdateRate(Double.POSITIVE_INFINITY);
                simSpeedLabel.setText("Max update rate: UNLIMITED");
            } else {
                environment.setMaxSimUpdateRate(newValue.doubleValue());
                simSpeedLabel.setText("Max update rate: " + newValue.intValue());
            }
        });
        simSpeed.setValue(60);//simSpeed.getMax());


        menuContent.getChildren().addAll(terrainOptions,  brushSizeLabel, brushSize, simSpeedLabel, simSpeed);

        collapsibleMenu.setText("Menu");
        collapsibleMenu.setContent(menuContent);
        collapsibleMenu.setAnimated(true);
        collapsibleMenu.setCollapsible(true);
        collapsibleMenu.setMaxWidth(200);
        collapsibleMenu.setFocusTraversable(false);

        // Align the floating menu to the top-left corner
        StackPane.setAlignment(collapsibleMenu, Pos.TOP_LEFT);
        StackPane.setMargin(collapsibleMenu, new Insets(15));
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