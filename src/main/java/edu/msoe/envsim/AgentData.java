package edu.msoe.envsim;

import edu.msoe.envsim.nn.NeuralNetwork;
import javafx.scene.paint.Color;

public record AgentData(Vector position, Color color, double energy, double speed, double size, double visionRadius,
                        double healthRegenSpeed, NeuralNetwork brain, NeuralNetwork foodEvaluator,
                        NeuralNetwork preyEvaluator, NeuralNetwork predatorEvaluator) {
}
