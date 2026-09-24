package edu.msoe.envsim;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class Environment extends Canvas {
    private List<Entity> entities = new ArrayList<>();
    private final GraphicsContext gc;

    public Environment(int width, int height) {
        super(width, height);
        gc = getGraphicsContext2D();

        reset();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                step();
            }
        };
        timer.start();
    }

    public void reset() {
        // Clear background
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        // Grid overlay
        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1.0);
        for (int x = 0; x < getWidth(); x += 40) {
            gc.strokeLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += 40) {
            gc.strokeLine(0, y, getWidth(), y);
        }
    }

    private void step() {
        reset();
        for (Entity entity : entities) {
            entity.step();
            entity.paint(gc);
        }
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public Vector getSize() {
        return new Vector(getWidth(), getHeight());
    }
}
