package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.utilities.NeuralNetwork;
import edu.msoe.envsim.utilities.Vector;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;


public record AgentData(Vector position, Color color, double energy, double speed, double turnSpeed, double size,
                        double visionRadius, double healthRegenSpeed, double swimSkill, NeuralNetwork brain, NeuralNetwork foodEvaluator,
                        NeuralNetwork preyEvaluator, NeuralNetwork predatorEvaluator) {
    public static final double MUTATION_CHANCE = 0.1;
    public static final double NEURON_MUTATION_SIZE = 0.02;
    public static final double ATTRIBUTE_MUTATION_SIZE = 0.2;

    private double mutateAttribute(double attribute) {
        return attribute + (attribute * (ThreadLocalRandom.current().nextDouble() - 0.5) * ATTRIBUTE_MUTATION_SIZE);
    }

    @Contract(" -> new")
    public @NotNull AgentData mutate() {
        return new AgentData(
            position().add(Vector.random(100 + size() * 2)),
            color(),
            this.energy, // TODO: Children should actually get the cost subtracted from each parent's health (What did I mean by this?)
            mutateAttribute(this.speed),
            mutateAttribute(this.turnSpeed),
            mutateAttribute(this.size),
            mutateAttribute(this.visionRadius),
            mutateAttribute(this.healthRegenSpeed),
            Math.clamp(mutateAttribute(this.swimSkill), 0, 1),
            this.brain.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE),
            this.foodEvaluator.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE),
            this.preyEvaluator.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE),
            this.predatorEvaluator.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE)
        );
    }
}
