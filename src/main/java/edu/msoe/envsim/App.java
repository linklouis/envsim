package edu.msoe.envsim;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
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

    private HBox debugBox;

    @Override
    public void start(@NotNull Stage stage) {
        int viewWidth = 1080;
        int viewHeight = 720;

        setupEnvironment(stage,  viewWidth, viewHeight);

        // Populate Entities
        for (int i = 0; i < 500; i++) {
            Color randomColor = Color.color(Math.random(), Math.random(), Math.random());

            new Agent(Math.random() * 30 + 3, randomColor, environment);
        }

        for (int i = 0; i < 2000; i++) {
            Vector position = Vector.random(Vector.zero, environment.getSize());
            new Food(position, Math.pow(Math.random(), 3) * 40 + 5, environment);
        }

        // Build Collapsible Overlay Menu
        TitledPane collapsibleMenu = createMenu();

        // Layer Controls over Canvas using StackPane
        StackPane gamePane = new StackPane();
        gamePane.getChildren().addAll(environment);//, collapsibleMenu);

        // Align the floating menu to the top-left corner
        StackPane.setAlignment(collapsibleMenu, Pos.TOP_LEFT);
        StackPane.setMargin(collapsibleMenu, new Insets(15));

        debugBox = new HBox();
        DebugMessage ticksPerSecond = new DebugMessage(
                () -> environment.getSimUpdateRate() + " TpS",
                environment
        );
        DebugMessage numAgents = new DebugMessage(
                () -> environment.getNumAgents() + " Agents",
                environment
        );
        debugBox.getChildren().addAll(ticksPerSecond, numAgents);

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

        setupKeyboardInputs(scene);
    }

    private void setupKeyboardInputs(@NotNull Scene scene) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case KeyCode.HOME -> {
                    toggleDebugMode();
                }
            }
        });
    }

    private void setupEnvironment(@NotNull Stage stage, int viewWidth, int viewHeight) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double aspectRatio = screenBounds.getHeight() / screenBounds.getWidth();
        int worldWidth = (int) (7000 * 1.0);
        int worldHeight = (int) (worldWidth * aspectRatio);
        double zoom = screenBounds.getWidth() / worldWidth;

        environment = new Environment(viewWidth, viewHeight, worldWidth, worldHeight, zoom, debugMode);

        environment.widthProperty().bind(stage.widthProperty());
        environment.heightProperty().bind(stage.heightProperty());
    }

    /**
     * Creats a temp menu
     * @return
     */
    private @NotNull TitledPane createMenu() {
        VBox menuContent = new VBox(10);
        menuContent.setPadding(new Insets(10));
        menuContent.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9);");

//        Button clearBtn = new Button("Clear Canvas");
//        clearBtn.setOnAction(e -> environment.reset());

//        Button drawRandomBtn = new Button("Draw Random Circles");
//        drawRandomBtn.setOnAction(e -> drawRandomShapes(environment.getGraphicsContext2D()));

//        menuContent.getChildren().addAll(new Label("Simulation Controls"), drawRandomBtn, clearBtn);

        TitledPane collapsibleMenu = new TitledPane("Menu", menuContent);
        collapsibleMenu.setAnimated(true);
        collapsibleMenu.setCollapsible(true);
        collapsibleMenu.setMaxWidth(200);

        return collapsibleMenu;
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