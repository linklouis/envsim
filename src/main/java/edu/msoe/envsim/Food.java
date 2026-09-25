package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public class Food extends Entity {
    private final static double RADIUS_VALUE_SCALE = 1;

    public Food(Vector position, double value, @NotNull Environment env) {
        super(position, value * RADIUS_VALUE_SCALE, Color.YELLOW, env);
    }

    @Override
    public void paint(@NotNull GraphicsContext gc) {
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
    }

    @Override
    public void step() {
        setRadius(getRadius() + 0.01);
    }

    public double getValue() {
        return getRadius() / RADIUS_VALUE_SCALE;
    }

    public void getEaten() {
        // TODO: Should grow over time?
        setRadius(0);
    }
}
