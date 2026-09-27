package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public class Food extends Entity {
    private final static double RADIUS_VALUE_SCALE = 1.6;
    private static final double GROWTH_SPEED = 0.002;
    private final double MAX_SIZE = Math.random() * 7 + 3;
    private static final int REGROWTH_TIME = 100;

    private int regrowthTimer = 0;

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
        if (regrowthTimer == 0) {
            setRadius(getRadius() + GROWTH_SPEED * (MAX_SIZE - getRadius()));
        } else {
            regrowthTimer--;
        }
    }

    public double getValue() {
        return getRadius() * RADIUS_VALUE_SCALE;
    }

    public void getEaten() {
        setRadius(0);
        regrowthTimer = REGROWTH_TIME;
    }
}
