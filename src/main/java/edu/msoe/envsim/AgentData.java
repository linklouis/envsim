package edu.msoe.envsim;

import javafx.scene.paint.Color;

public record AgentData(Vector position, Color color, double energy, double speed, double size, double visionRadius) {
}
