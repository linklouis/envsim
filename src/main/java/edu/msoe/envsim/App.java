package edu.msoe.envsim;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class App extends Application {

    private Environment environment;

    @Override
    public void start(@NotNull Stage stage) {
        int viewWidth = 2500;
        int viewHeight = 1300;
        int worldWidth = (int) (viewWidth * 2.5);//(int) (5060 * 1.3);
        int worldHeight = (int) (viewHeight * 2.5);//(int) (2944 * 1.3);

        environment = new Environment(viewWidth, viewHeight, worldWidth, worldHeight);

        for (int i = 0; i < 500; i++) {
            Color randomColor = Color.color(Math.random(), Math.random(), Math.random());

            new Agent(Math.random() * 25 + 3, randomColor, environment);
        }

        for (int i = 0; i < 1200; i++) {
            Vector position = Vector.random(Vector.zero, environment.getSize());
            new Food(position, Math.random() * 5, environment);
        }

        // Build Collapsible Overlay Menu
        TitledPane collapsibleMenu = createMenu();

        // Layer Controls over Canvas using StackPane
        StackPane root = new StackPane();
        root.getChildren().addAll(environment, collapsibleMenu);

        // Align the floating menu to the top-left corner
        StackPane.setAlignment(collapsibleMenu, Pos.TOP_LEFT);
        StackPane.setMargin(collapsibleMenu, new Insets(15));

        Scene scene = new Scene(root, viewWidth, viewHeight);
        stage.setScene(scene);
        stage.setTitle("Environment Simulation - JavaFX 27");
        stage.show();
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
}