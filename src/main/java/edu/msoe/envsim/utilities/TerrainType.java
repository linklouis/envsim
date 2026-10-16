package edu.msoe.envsim.utilities;

import javafx.scene.paint.Color;

public enum TerrainType {
    STANDARD(new Color(0.03, 0.8, 0.2, 1)),
    WATER(new Color(0.02, 0.1, 0.9, 1));

    private Color color;

    TerrainType(Color color) {
        this.color = color;
    }
}
