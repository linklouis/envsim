package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public abstract class Entity {
    private Vector position;
    private Color color;
    private double radius;
    private Vector cellIndex;
    private volatile boolean registered;
    private final Function<Entity, Vector> updateCell;

    public Entity(@NotNull Vector position, double radius, Color color, @NotNull Environment env) {
        this.radius = radius;
        this.color = color;
        this.updateCell = env::updateCell;
        this.position = position;
        env.addEntity(this);
    }

    public abstract void parallelStep();

    public void step() {
    }

    public abstract void paint(GraphicsContext gc, boolean isDebugMode);

    public boolean isColliding(@NotNull Entity other) {
        double r = radius + other.getRadius();
        return getPosition().distanceSquared(other.getPosition()) <= r * r;
    }

    /** Called by the Environment when this entity is flushed into the active simulation. */
    public void onRegistered() {
        cellIndex = updateCell.apply(this);
        registered = true;
    }

    public boolean isRegistered() {
        return registered;
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
        this.cellIndex = updateCell.apply(this);
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
    public Vector getCellIndex() {
        return cellIndex;
    }
}
