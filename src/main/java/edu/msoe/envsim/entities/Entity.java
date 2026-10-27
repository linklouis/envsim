package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public abstract class Entity {
    private Vector position;
    private Color color;
    private double radius;

    public Entity(@NotNull Vector position, double radius, Color color, @NotNull Environment env) {
        this.radius = radius;
        this.color = color;
        this.position = position;
        env.addEntity(this); // Automatically routes to mobile or stationary list
    }

    public abstract void parallelStep();

    public void step() {
    }

    public abstract void paint(GraphicsContext gc, boolean isDebugMode);

    public boolean isColliding(@NotNull Entity other) {
        double r = radius + other.getRadius();
        return getPosition().distanceSquared(other.getPosition()) <= r * r;
    }

    public double distance(@NotNull Entity other) {
        return other.getPosition().distance(getPosition());
    }

    public Vector directionTo(@NotNull Entity other) {
        return other.getPosition().subtract(getPosition());
    }

    public Vector getPosition() {
        return position;
    }

    protected void setPosition(Vector position) {
        this.position = position;
    }

    public double x() {
        return position.x();
    }
    public double y() {
        return position.y();
    }
    protected Color getColor() {
        return color;
    }
    protected void setColor(Color color) {
        this.color = color;
    }
    public double getRadius() {
        return radius;
    }
    protected void setRadius(double radius) {
        this.radius = radius;
    }
}