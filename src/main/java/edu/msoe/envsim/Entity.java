package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public abstract class Entity {
    private Vector position;
    private Color color;
    private double radius;

    public Entity(Vector position, double radius, Color color, @NotNull Environment env) {
        this.position = position;
        this.radius = radius;
        this.color = color;
        env.addEntity(this);
    }

    public void step() {
        // Default: stationary entities do nothing on step
    }

    public abstract void paint(GraphicsContext gc);

    public boolean isColliding(@NotNull Entity other) {
        return getPosition().distance(other.getPosition()) <= radius + other.getRadius();
    }



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
    protected double getRadius() {
        return radius;
    }
    protected void setRadius(double radius) {
        this.radius = radius;
    }
}
