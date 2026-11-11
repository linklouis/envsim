package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class Food extends StationaryEntity {
    private final static double RADIUS_VALUE_SCALE = 2;
    private static final double GROWTH_SPEED = 0.002;
    private static final int REGROWTH_TIME = 100;

    private final double maxSize;
    private int regrowthTimer = 0;
    private FoodType foodType;

    // TODO: Might make actual types in the future, but this works for now
    private enum FoodType {
        TYPE_1(new Color(0.82, 0.72, 0.16, 1)),
        TYPE_2(new Color(0.68, 0.14, 0.14, 1)),
        TYPE_3(new Color(0.16, 0.25, 0.55, 1));

        private final Color color;

        FoodType(final Color color) {
            this.color = color;
        }

        public Color getColor() {
            return color;
        }
    }

    public record FoodPreference(double type1Preference, double type2Preference, double type3Preference) {
        // TODO: Could update a few of these functions to be general for any number of food types using arrays, but would technically be slower

        @Contract(" -> new")
        public static @NotNull FoodPreference random() {
            return new FoodPreference(
                    ThreadLocalRandom.current().nextDouble(),
                    ThreadLocalRandom.current().nextDouble(),
                    ThreadLocalRandom.current().nextDouble()
            );
        }

        private double mutateOne(double value, double mutationSize) {
            double change = value * (ThreadLocalRandom.current().nextDouble() - 0.5) * mutationSize;
            return Math.clamp(value + change, 0, 1);
        }

        @Contract("_ -> new")
        public @NotNull FoodPreference mutate(double mutationSize) {
            return new FoodPreference(
                    mutateOne(type1Preference, mutationSize),
                    mutateOne(type2Preference, mutationSize),
                    mutateOne(type3Preference, mutationSize)
            );
        }

        @Contract(pure = true)
        private double getCompatibility(@NotNull FoodType foodType) {
            return switch (foodType) {
                case TYPE_1 -> type1Preference;
                case TYPE_2 -> type2Preference;
                case TYPE_3 -> type3Preference;
            };
        }

        public void writeValues(@NotNull DataOutputStream out) throws IOException {
            out.writeDouble(type1Preference);
            out.writeDouble(type2Preference);
            out.writeDouble(type3Preference);
        }

        public double calculatePerTickCost(double costFactor) {
            return (type1Preference + type2Preference + type3Preference) * costFactor;
        }
    }

    public Food(Vector position, double maxSize, @NotNull Environment env) {
        FoodType foodType = FoodType.values()[ThreadLocalRandom.current().nextInt(0, 3)];
        super(position, maxSize, foodType.getColor(), env);
        this.foodType = foodType;
        this.maxSize = maxSize;
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

    public double getValue(@NotNull FoodPreference foodPreference) {
        return getRadius() * RADIUS_VALUE_SCALE * getTerrainType().getFoodValue() * foodPreference.getCompatibility(foodType);
    }

    /**
     * Source: Google Gemini
     * @param targetConsumptionFraction The amount of the Food intended to be eaten as a portion of its current size
     */
    public void getEaten(double targetConsumptionFraction) {
        double currentRadius = getRadius();
        double radiusRemoved = currentRadius * Math.min(1.0, targetConsumptionFraction);

        double newRadius = currentRadius - radiusRemoved;
        setRadius(Math.max(0, newRadius));

        if (getRadius() <= 0) {
            regrowthTimer = REGROWTH_TIME;
        }
    }

    public float getFoodType() {
        return (float) foodType.ordinal() / FoodType.values().length;
    }
}
