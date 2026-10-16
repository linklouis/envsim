package edu.msoe.envsim.utilities;

import edu.msoe.envsim.nn.NeuralNetwork;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpeciesColorMapper {

    private final float lightness = 0.65f;
    private final float maxRadius = 0.18f;
    private float[][] projectionMatrix;
    private final long seed = 42L;

    public SpeciesColorMapper() {}

    /**
     * Calculates base species color (Hue & Saturation at constant lightness) from explicit genetic parameters.
     */
    public Color getBaseSpeciesColor(double maxSpeed, double size, double visionRadius, double healthRegenSpeed,
                                     @NotNull NeuralNetwork brain, @NotNull NeuralNetwork foodEvaluator,
                                     @NotNull NeuralNetwork preyEvaluator, @NotNull NeuralNetwork predatorEvaluator) {
        float[] genome = extractGenome(maxSpeed, size, visionRadius, healthRegenSpeed, brain, foodEvaluator, preyEvaluator, predatorEvaluator);

        if (projectionMatrix == null || projectionMatrix[0].length != genome.length) {
            initializeMatrix(genome.length);
        }

        float[] rgb = getSpeciesRGB(genome);
        return Color.color(rgb[0], rgb[1], rgb[2]);
    }

    private void initializeMatrix(int numInputs) {
        projectionMatrix = new float[2][numInputs];
        Random random = new Random(seed);
        for (int i = 0; i < numInputs; i++) {
            projectionMatrix[0][i] = (float) (random.nextGaussian() * 0.35);
            projectionMatrix[1][i] = (float) (random.nextGaussian() * 0.35);
        }
    }

    private float @NotNull [] extractGenome(double maxSpeed, double size, double visionRadius, double healthRegenSpeed,
                                            @NotNull NeuralNetwork brain, @NotNull NeuralNetwork foodEvaluator,
                                            @NotNull NeuralNetwork preyEvaluator, @NotNull NeuralNetwork predatorEvaluator) {
        List<Float> traits = new ArrayList<>();

        traits.add((float) maxSpeed);
        traits.add((float) size);
        traits.add((float) visionRadius);
        traits.add((float) healthRegenSpeed);

        traits.addAll(brain.getWeights());
        traits.addAll(foodEvaluator.getWeights());
        traits.addAll(preyEvaluator.getWeights());
        traits.addAll(predatorEvaluator.getWeights());

        float[] genome = new float[traits.size()];
        for (int i = 0; i < traits.size(); i++) {
            genome[i] = traits.get(i);
        }
        return genome;
    }

    private float @NotNull [] getSpeciesRGB(float @NotNull [] genome) {
        float a = 0.0f;
        float b = 0.0f;

        for (int i = 0; i < genome.length; i++) {
            float squashed = (float) Math.tanh(genome[i]);
            a += projectionMatrix[0][i] * squashed;
            b += projectionMatrix[1][i] * squashed;
        }

        float radius = (float) Math.hypot(a, b);
        if (radius > 0.0f) {
            float scale = (float) (Math.tanh(radius / maxRadius) / (radius / maxRadius));
            a *= scale;
            b *= scale;
        }

        return oklabToSrgb(this.lightness, a, b);
    }

    @Contract("_, _, _ -> new")
    private float @NotNull [] oklabToSrgb(float L, float a, float b) {
        float l_ = L + 0.3963377774f * a + 0.2158037573f * b;
        float m_ = L - 0.1055613458f * a - 0.0638541728f * b;
        float s_ = L - 0.0894841775f * a - 1.2914855480f * b;

        float l = l_ * l_ * l_;
        float m = m_ * m_ * m_;
        float s = s_ * s_ * s_;

        float rLinear = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s;
        float gLinear = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s;
        float bLinear = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;

        float r = Math.clamp(linearToSrgb(rLinear), 0.0f, 1.0f);
        float g = Math.clamp(linearToSrgb(gLinear), 0.0f, 1.0f);
        float bComponent = Math.clamp(linearToSrgb(bLinear), 0.0f, 1.0f);

        return new float[]{r, g, bComponent};
    }

    private float linearToSrgb(float c) {
        if (c <= 0.0031308f) {
            return 12.92f * c;
        } else {
            return 1.055f * (float) Math.pow(c, 1.0 / 2.4) - 0.055f;
        }
    }
}