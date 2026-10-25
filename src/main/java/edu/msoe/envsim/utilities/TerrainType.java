package edu.msoe.envsim.utilities;

import javafx.scene.paint.Color;

public enum TerrainType {
    STANDARD(new Color(0.1, 0.45, 0.2, 1), 1.0/40),
    WATER(new Color(0.004, 0.329, 0.6, 1), 1.0/50);

    private Color color;
    /**
     * How the factor to multiply radius by when calculating how much size should affect speed.
     */
    private double speedSizeCostFactor;

    TerrainType(Color color,  double speedSizeCostFactor) {
        this.color = color;
        this.speedSizeCostFactor = speedSizeCostFactor;
    }

    public Color getColor() {
        return color;
    }

    public double getSpeedSizeCostFactor() {
        return speedSizeCostFactor;
    }
}
