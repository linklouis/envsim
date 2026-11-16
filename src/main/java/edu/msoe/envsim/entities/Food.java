
package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.apputils.GameSnapshot.FoodState;
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
    private static final double MIN_FOOD_PREFERENCE_WITHOUT_POISON = 0.3;

    private final double maxSize;
    private int regrowthTimer = 0;
    private final FoodType foodType;

    // TODO: Might make actual types W/ names in the future, but this works for now
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

        /**
         * Source: Google Gemini
         * @param logits
         * @return
         */
        @Contract("null -> fail")
        public static double @NotNull [] softmax(double[] logits) {
            if (logits == null || logits.length == 0) {
                throw new IllegalArgumentException("Input array must not be null or empty");
            }

            // 1. Find the maximum value for numerical stability
            double max = Double.NEGATIVE_INFINITY;
            for (double val : logits) {
                if (val > max) {
                    max = val;
                }
            }

            // 2. Compute exponentials (shifted by max) and their sum
            double[] exps = new double[logits.length];
            double sum = 0.0;
            for (int i = 0; i < logits.length; i++) {
                exps[i] = Math.exp(logits[i] - max);
                sum += exps[i];
            }

            // 3. Normalize to get probabilities
            double[] probabilities = new double[logits.length];
            for (int i = 0; i < logits.length; i++) {
                probabilities[i] = Math.min(2 * exps[i] / sum, 1);
            }

            return probabilities;
        }

        @Contract(" -> new")
        public static @NotNull FoodPreference random() {
            double[] preferences = new double[] {
                    ThreadLocalRandom.current().nextDouble(),
                    ThreadLocalRandom.current().nextDouble(),
                    ThreadLocalRandom.current().nextDouble()
            };
//            preferences = softmax(preferences);
            return new FoodPreference(
                    preferences[0],
                    preferences[1],
                    preferences[2]
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

        private static double calculateCompatibilityNoNegative(double preference) {
            return Math.pow(preference - MIN_FOOD_PREFERENCE_WITHOUT_POISON, 3);
        }

        /**
         * TODO: Make the exponent a hyperparameter? (Would have to add the Math.abs() back in)
         * TODO: Move redundant calculations?
         * @param preference The preference value for a given {@link FoodType}
         * @return The factor that should be applied in the calculation of the energy value that a given {@link Food} should provide
         */
        private static double calculateCompatibility(double preference) {
            return Math.signum(preference - MIN_FOOD_PREFERENCE_WITHOUT_POISON) * Math.pow((preference - MIN_FOOD_PREFERENCE_WITHOUT_POISON) / (1 - MIN_FOOD_PREFERENCE_WITHOUT_POISON), 2);
        }

        @Contract(pure = true)
        private double getCompatibility(@NotNull FoodType foodType) {
            return switch (foodType) {
                case TYPE_1 -> calculateCompatibility(type1Preference);
                case TYPE_2 -> calculateCompatibility(type2Preference);
                case TYPE_3 -> calculateCompatibility(type3Preference);
            };
        }

        public void writeValues(@NotNull DataOutputStream out) throws IOException {
            out.writeDouble(type1Preference);
            out.writeDouble(type2Preference);
            out.writeDouble(type3Preference);
        }

        public double getTotalPreferenceAmount() {
            return type1Preference + type2Preference + type3Preference;
        }
    }

    public Food(Vector position, double maxSize, @NotNull Environment env) {
        FoodType foodType = FoodType.values()[ThreadLocalRandom.current().nextInt(0, 3)];
        super(position, maxSize, foodType.getColor(), env);
        this.foodType = foodType;
        this.maxSize = maxSize;
    }

    /**
     * Recreates a piece of food exactly as it was saved: same type, current size and regrowth progress.
     *
     * @param foodTypeIndex The food type's ordinal, as returned by {@link #getFoodTypeIndex()}
     */
    public Food(Vector position, double maxSize, double radius, int foodTypeIndex, int regrowthTimer, @NotNull Environment env) {
        FoodType foodType = FoodType.values()[foodTypeIndex];
        super(position, maxSize, foodType.getColor(), env);
        this.foodType = foodType;
        this.maxSize = maxSize;
        setRadius(Math.clamp(radius, 0, maxSize));
        this.regrowthTimer = regrowthTimer;
    }

    public static int getFoodTypeCount() {
        return FoodType.values().length;
    }

    /**
     * @param foodTypeIndex The food type's ordinal, as returned by {@link #getFoodTypeIndex()}
     * @return The color food of that type is drawn in
     */
    public static @NotNull Color getTypeColor(int foodTypeIndex) {
        return FoodType.values()[foodTypeIndex].getColor();
    }

    public int getFoodTypeIndex() {
        return foodType.ordinal();
    }

    public @NotNull FoodState captureState() {
        return new FoodState(x(), y(), maxSize, getRadius(), getFoodTypeIndex(), regrowthTimer);
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
        try {
            setRadius(Math.max(0, newRadius));
        } catch (IllegalArgumentException e) {
            System.out.println(currentRadius);
            System.out.println(radiusRemoved);
            throw e;
        }

        if (getRadius() <= 0) {
            regrowthTimer = REGROWTH_TIME;
        }
    }

    @Override
    public void setRadius(double radius) {
        if (radius > maxSize) throw new IllegalArgumentException("radius > maxSize:" + radius);
        super.setRadius(radius);
    }

    public float getFoodType() {
        return (float) foodType.ordinal() / FoodType.values().length;
    }
}
 