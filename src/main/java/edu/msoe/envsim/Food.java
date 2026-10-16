package edu.msoe.envsim;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public class Food extends Entity {
    private final static double RADIUS_VALUE_SCALE = 2;
    private static final double GROWTH_SPEED = 0.002;
    private static final int REGROWTH_TIME = 100;

    private final double maxSize;
    private int regrowthTimer = 0;

    public Food(Vector position, double maxSize, @NotNull Environment env) {
        super(position, maxSize, Color.YELLOW, env);
        this.maxSize = maxSize;
    }

    @Override
    public void paint(@NotNull GraphicsContext gc) {
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
    }

    @Override
    public void step() {
        if (regrowthTimer == 0) {
            setRadius(getRadius() + GROWTH_SPEED * (maxSize - getRadius()));
        } else {
            regrowthTimer--;
        }
    }

    public double getValue() {
        return getRadius() * RADIUS_VALUE_SCALE;
    }

    public void getEaten(double consumptionSize) {
        setRadius(getRadius() -  getRadius() * consumptionSize);
        if (getRadius() <= 0) {
            regrowthTimer = REGROWTH_TIME;
        }
    }
}
