package edu.msoe.envsim.utilities.simutils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public abstract class DimensionalMapper {
    private final static long SEED = 42L;
    private static volatile float[][] projectionMatrix;

    /**
     * Projects a high-dimensional vector into a lower-dimensional representation.
     */
    protected static float @NotNull [] getProjection(float @NotNull [] vector, int numOutputs, float maxRadius) {
        getOrCreateMatrix(vector.length, numOutputs);
        return getProjection(vector, projectionMatrix, maxRadius);
    }

    private static void getOrCreateMatrix(int numInputs, int numOutputs) {
        // Local reference read avoids volatile read overhead and partial-initialization races
        float[][] localMatrix = projectionMatrix;

        // Safe check: Only check row dimensions if the matrix itself isn't null
        if (localMatrix == null || localMatrix.length != numOutputs || localMatrix[0].length != numInputs) {
            synchronized (SpeciesColorMapper.class) {
                if (projectionMatrix == null || projectionMatrix.length != numOutputs || projectionMatrix[0].length != numInputs) {
                    initializeMatrix(numInputs, numOutputs);
                }
            }
        }
    }

    private static void initializeMatrix(int numInputs, int numOutputs) {
        // Construct completely locally first before publishing to the volatile field
        float[][] tempMatrix = new float[numOutputs][numInputs];
        Random random = new Random(SEED);
        for (int i = 0; i < numInputs; i++) {
            for (int j = 0; j < numOutputs; j++) {
                tempMatrix[j][i] = (float) (random.nextGaussian() * 0.4);
            }
        }
        projectionMatrix = tempMatrix; // Atomically publish fully formed matrix
    }

    @Contract("_, _, _ -> new")
    private static float @NotNull [] getProjection(float @NotNull [] input, float @NotNull [] @NotNull [] matrix, float maxRadius) {
        int numOutputs = matrix.length;
        float[] output = new float[numOutputs];

        for (int i = 0; i < input.length; i++) {
            float squashed = (float) Math.tanh(input[i]);
            for (int j = 0; j < numOutputs; j++) {
                output[j] += matrix[j][i] * squashed;
            }
        }

        float sumOfSquares = 0.0f;
        for (float val : output) {
            sumOfSquares += val * val;
        }
        float radius = (float) Math.sqrt(sumOfSquares);

        if (radius > 0.0f) {
            float scale = (float) (Math.tanh(radius / maxRadius) / (radius / maxRadius));
            for (int j = 0; j < output.length; j++) {
                output[j] *= scale;
            }
        }

        return output;
    }
}

