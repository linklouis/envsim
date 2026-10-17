package edu.msoe.envsim.utilities;

import javafx.scene.paint.Color;

public enum TerrainType {
    STANDARD(new Color(0.1, 0.45, 0.2, 1)),
    WATER(new Color(0.004, 0.329, 0.6, 1));

    private Color color;

    TerrainType(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
