package edu.msoe.envsim.utilities;

import edu.msoe.envsim.nn.NeuralNetwork;
import javafx.scene.paint.Color;

public record AgentData(Vector position, Color color, double energy, double speed, double turnSpeed, double size,
                        double visionRadius, double healthRegenSpeed, double swimSkill, NeuralNetwork brain, NeuralNetwork foodEvaluator,
                        NeuralNetwork preyEvaluator, NeuralNetwork predatorEvaluator) {
}
