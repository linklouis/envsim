package edu.msoe.envsim.utilities.simutils;

import javafx.scene.paint.Color;

public enum TerrainType {
    STANDARD(new Color(0.1, 0.45, 0.2, 1), 0.05),
    WATER(new Color(0.004, 0.329, 0.6, 1), 0.02);

    private final Color color;
    private final double terrainResistance;

    TerrainType(Color color, double terrainResistance) {
        this.color = color;
        this.terrainResistance = terrainResistance;
    }

    public double getTerrainResistance() {
        return terrainResistance;
    }

    public Color getColor() {
        return color;
    }
}
