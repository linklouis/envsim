package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public abstract class Entity {
    private Vector position;
    private Color color;

    public Entity(Vector position, Color color, @NotNull Environment env) {
        this.position = position;
        this.color = color;
        env.addEntity(this);
    }

    public void step() {
        // Default: stationary entities do nothing on step
    }

    public abstract void paint(GraphicsContext gc);

    protected Vector getPosition() {
        return position;
    }

    protected void setPosition(Vector position) {
        this.position = position;
    }

    protected double x() {
        return position.x();
    }
    protected double y() {
        return position.y();
    }
    protected Color getColor() {
        return color;
    }
}
