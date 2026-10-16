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
    private Vector cell;
    private final Function<Entity, Vector> updateCell;

    public Entity(@NotNull Vector position, double radius, Color color, @NotNull Environment env) {
        this.radius = radius;
        this.color = color;
        this.updateCell = env::updateCell;
        env.addEntity(this);
        setPosition(position);
    }

    public void getAction() {

    }

    public void step() {
        // Default: stationary entities do nothing on step
    }

    public abstract void paint(GraphicsContext gc);

    public boolean isColliding(@NotNull Entity other) {
        return getPosition().distance(other.getPosition()) <= radius + other.getRadius();
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
        this.cell = updateCell.apply(this);
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
    public Vector getCell() {
        return cell;
    }
}
