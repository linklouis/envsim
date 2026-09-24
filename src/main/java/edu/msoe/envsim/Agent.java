package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class Agent  extends Entity {
    private Vector direction;
    private double radius;
    private Supplier<Vector> boundsSupplier;

    public Agent(double radius, Color color, @NotNull Environment env) {
        Vector position = Vector.random(new Vector(0, 0), new Vector(env.getWidth(), env.getHeight()));
        super(position, color, env);

        direction = Vector.random(1);
        this.radius = radius;
        boundsSupplier = env::getSize;
    }

    @Override
    public void step() {
        setPosition(getPosition().add(direction));
        direction = direction.turn((Math.random() - 0.5) * 20);

        setPosition(getPosition().clamp(Vector.zero, getBounds()));
    }

    @Override
    public void paint(@NotNull GraphicsContext gc) {
        gc.setFill(getColor());
        gc.fillOval(x() - radius / 2, y() - radius / 2, radius, radius);
    }


    /*
     * Getters and Setters
     */

    @Override
    public Vector getPosition() {
        return super.getPosition();
    }

    public Vector getDirection() {
        return direction;
    }

    private Vector getBounds() {
        return boundsSupplier.get();
    }
}
