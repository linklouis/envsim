package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.simutils.TerrainType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public class Food extends Entity {
    private final static double RADIUS_VALUE_SCALE = 1.9;
    private static final double GROWTH_SPEED = 0.002;
    private static final int REGROWTH_TIME = 100;

    private final double maxSize;
    private int regrowthTimer = 0;
    private TerrainType terrainType;

    public Food(Vector position, double maxSize, @NotNull Environment env) {
        super(position, maxSize, Color.YELLOW, env);
        this.maxSize = maxSize;
        terrainType = env.getTerrainType(getPosition());
    }

    @Override
    public void paint(@NotNull GraphicsContext gc, boolean isDebugMode) {
        gc.setFill(getColor());
        gc.fillOval(x() - getRadius(), y() - getRadius(), getRadius() * 2, getRadius() * 2);
    }

    @Override
    public void parallelStep() {
        if (regrowthTimer > 0) {
            regrowthTimer--;
            return;
        }
        double r = getRadius();
        if (r >= maxSize) return;
        r += GROWTH_SPEED * (maxSize - r);
        setRadius(maxSize - r < 1e-4 ? maxSize : r);
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

    public TerrainType getTerrainType() {
        return terrainType;
    }
}
