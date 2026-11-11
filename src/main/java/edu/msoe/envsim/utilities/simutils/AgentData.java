package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Food;
import edu.msoe.envsim.utilities.NeuralNetwork;
import edu.msoe.envsim.utilities.Vector;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;


public record AgentData(Vector position, Color color, double energy, double speed, double turnSpeed, double size,
                        double visionRadius, double healthRegenSpeed, double swimSkill, double attack, double defense,
                        double fieldOfView, Food.FoodPreference foodPreference, NeuralNetwork brain, int generation) {
    public static final double MUTATION_CHANCE = 0.1;
    public static final double NEURON_MUTATION_SIZE = 0.02;
    public static final double ATTRIBUTE_MUTATION_SIZE = 0.2;

    private double mutateAttribute(double attribute) {
        return attribute + (attribute * (ThreadLocalRandom.current().nextDouble() - 0.5) * ATTRIBUTE_MUTATION_SIZE);
    }

    public AgentData(@NotNull AgentData data, Vector position) {
        this(position, data.color(), data.energy(), data.speed(),
                data.turnSpeed(), data.size(), data.visionRadius(), data.healthRegenSpeed(),
                data.swimSkill(), data.attack(), data.defense(), data.fieldOfView(),
                data.foodPreference(), data.brain(), data.generation());
    }

    @Contract(" -> new")
    public @NotNull AgentData mutate() {
        return new AgentData(
                position().add(Vector.random(100 + size() * 2)),
                color(),
                energy, // TODO: Children should actually get the cost subtracted from each parent's health (What did I mean by this?)
                mutateAttribute(speed),
                mutateAttribute(turnSpeed),
                Math.max(mutateAttribute(size), Agent.MIN_SIZE),
                mutateAttribute(visionRadius),
                mutateAttribute(healthRegenSpeed),
                Math.clamp(mutateAttribute(swimSkill), 0, 1),
                mutateAttribute(attack),
                mutateAttribute(defense),
                mutateAttribute(fieldOfView),
                foodPreference.mutate(ATTRIBUTE_MUTATION_SIZE),
                this.brain.child(MUTATION_CHANCE, NEURON_MUTATION_SIZE),
                generation + 1
        );
    }

    /**
     * Source: Google Gemini
     * @param file
     * @throws IOException
     */
    public void saveToFile(@NotNull File file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeDouble(position.x());
            out.writeDouble(position.y());

            out.writeDouble(color.getRed());
            out.writeDouble(color.getGreen());
            out.writeDouble(color.getBlue());
            out.writeDouble(color.getOpacity());

            out.writeDouble(energy);
            out.writeDouble(speed);
            out.writeDouble(turnSpeed);
            out.writeDouble(size);
            out.writeDouble(visionRadius);
            out.writeDouble(healthRegenSpeed);
            out.writeDouble(swimSkill);
            out.writeDouble(attack);
            out.writeDouble(defense);
            out.writeDouble(fieldOfView);
            foodPreference.writeValues(out);
            out.writeInt(generation);

            // Serialize Neural Networks
            brain.save(out);
        }
    }

    /**
     * Source: Google Gemini
     * @param file
     * @return
     * @throws IOException
     */
    public static @NotNull AgentData loadFromFile(@NotNull File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            double px = in.readDouble();
            double py = in.readDouble();

            Color color = new Color(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());

            double energy = in.readDouble();
            double speed = in.readDouble();
            double turnSpeed = in.readDouble();
            double size = in.readDouble();
            double visionRadius = in.readDouble();
            double healthRegenSpeed = in.readDouble();
            double swimSkill = in.readDouble();
            double attack = in.readDouble();
            double defense = in.readDouble();
            double fieldOfView = in.readDouble();
            Food.FoodPreference foodPreference = new Food.FoodPreference(in.readDouble(), in.readDouble(), in.readDouble());
            int generation = in.readInt();

            NeuralNetwork brain = NeuralNetwork.load(in);

            return new AgentData(new Vector(px, py), color, energy, speed, turnSpeed, size,
                    visionRadius, healthRegenSpeed, swimSkill, attack, defense, fieldOfView,
                    foodPreference, brain, generation);
        }
    }
}
