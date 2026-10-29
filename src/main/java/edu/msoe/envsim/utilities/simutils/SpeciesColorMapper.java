package edu.msoe.envsim.utilities.simutils;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class SpeciesColorMapper {
    private final static float LIGHTNESS = 0.65f;
    /**
     * TODO: What does this actually do?
     */
    private final static float MAX_RADIUS = 0.18f;
    private final static long SEED = 42L;

    /**
     * Calculates base species color (Hue & Saturation at constant lightness) from explicit genetic parameters.
     */
    public static @NotNull Color getBaseSpeciesColor(float @NotNull [] genome) {
        float[][] projectionMatrix = initializeMatrix(genome.length);
        float[] rgb = getSpeciesRGB(genome, projectionMatrix);
        return Color.color(rgb[0], rgb[1], rgb[2]);
    }

    private static float @NotNull [][] initializeMatrix(int numInputs) {
        float[][] projectionMatrix = new float[2][numInputs];
        Random random = new Random(SEED);
        for (int i = 0; i < numInputs; i++) {
            projectionMatrix[0][i] = (float) (random.nextGaussian() * 0.35);
            projectionMatrix[1][i] = (float) (random.nextGaussian() * 0.35);
        }
        return projectionMatrix;
    }

    private static float @NotNull [] getSpeciesRGB(float @NotNull [] genome, float[][] projectionMatrix) {
        float a = 0.0f;
        float b = 0.0f;

        for (int i = 0; i < genome.length; i++) {
            float squashed = (float) Math.tanh(genome[i]);
            a += projectionMatrix[0][i] * squashed;
            b += projectionMatrix[1][i] * squashed;
        }

        float radius = (float) Math.hypot(a, b);
        if (radius > 0.0f) {
            float scale = (float) (Math.tanh(radius / MAX_RADIUS) / (radius / MAX_RADIUS));
            a *= scale;
            b *= scale;
        }

        return oklabToSrgb(a, b);
    }

    @Contract("_, _ -> new")
    private static float @NotNull [] oklabToSrgb(float a, float b) {
        float l_ = LIGHTNESS + 0.3963377774f * a + 0.2158037573f * b;
        float m_ = LIGHTNESS - 0.1055613458f * a - 0.0638541728f * b;
        float s_ = LIGHTNESS - 0.0894841775f * a - 1.2914855480f * b;

        float l = l_ * l_ * l_;
        float m = m_ * m_ * m_;
        float s = s_ * s_ * s_;

        float rLinear = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s;
        float gLinear = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s;
        float bLinear = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;

        float r = Math.clamp(linearToSrgb(rLinear), 0.0f, 1.0f);
        float g = Math.clamp(linearToSrgb(gLinear), 0.0f, 1.0f);
        float bComponent = Math.clamp(linearToSrgb(bLinear), 0.0f, 1.0f);

        return new float[]{r, g, bComponent};
    }

    private static float linearToSrgb(float c) {
        if (c <= 0.0031308f) {
            return 12.92f * c;
        } else {
            return 1.055f * (float) Math.pow(c, 1.0 / 2.4) - 0.055f;
        }
    }
}