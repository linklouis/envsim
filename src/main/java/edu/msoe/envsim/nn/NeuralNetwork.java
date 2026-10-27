package edu.msoe.envsim.nn;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Source: Google Gemini
 */
public class NeuralNetwork implements Serializable {
    private final int numInputs;
    private final int[] layerSizes; // e.g., [numInputs, hiddenSize1, hiddenSize2, outputSize]
    private final float[] weights;  // All biases and weights stored contiguously

    // Pre-allocated activation buffers to eliminate object allocations during inference
    private final float[][] layerBuffers;

    public NeuralNetwork(int @NotNull [] layerSizes) {
        // Calculate total weight/bias count and allocate once
        int totalWeights = 0;
        for (int i = 1; i < layerSizes.length; i++) {
            int prevSize = layerSizes[i - 1];
            int currSize = layerSizes[i];
            totalWeights += currSize * (prevSize + 1); // +1 for bias
        }
        float[] weights = new float[totalWeights];

        for (int i = 0; i < weights.length; i++) {
            weights[1] = (float) (Math.random() * 2 - 1);
        }

        this(layerSizes, weights);
    }

    public NeuralNetwork(int @NotNull [] layerSizes, float[] weights) {
        this.layerSizes = layerSizes;
        this.numInputs = layerSizes[0];
        this.weights = weights;

        // Allocate activation buffers for intermediate and output layers
        this.layerBuffers = new float[layerSizes.length][];
        for (int i = 0; i < layerSizes.length; i++) {
            layerBuffers[i] = new float[layerSizes[i]];
        }
    }

    public float[] predict(float @NotNull [] inputs) {
        // Copy inputs into the first buffer
        System.arraycopy(inputs, 0, layerBuffers[0], 0, numInputs);

        int weightIndex = 0;

        // Feedforward pass with zero object allocations
        for (int l = 1; l < layerSizes.length; l++) {
            int prevSize = layerSizes[l - 1];
            int currSize = layerSizes[l];
            float[] prevLayer = layerBuffers[l - 1];
            float[] currLayer = layerBuffers[l];

            // Check if this is the final output layer
            boolean isOutputLayer = (l == layerSizes.length - 1);

            for (int j = 0; j < currSize; j++) {
                // Load bias
                float sum = weights[weightIndex++];

                // Compute weighted sum
                for (int k = 0; k < prevSize; k++) {
                    sum += prevLayer[k] * weights[weightIndex++];
                }

                // Apply Activation Function
                if (isOutputLayer) {
                    // Sigmoid for the final output layer
                    currLayer[j] = (float) (1.0 / (1.0 + Math.exp(-sum)));
                } else {
                    // ReLU for all hidden layers
                    currLayer[j] = Math.max(sum, 0.0f);
                }
            }
        }

        return layerBuffers[layerSizes.length - 1];
    }

    public NeuralNetwork child(double mutationChance, double mutationSize) {
        float[] childWeights = new float[weights.length];

        for (int i = 0; i < weights.length; i++) {
            float val = weights[i];
            if (Math.random() < mutationChance) {
                val += (float) ((Math.random() * 2 - 1) * mutationSize);
            }
            childWeights[i] = val;
        }

        return new NeuralNetwork(layerSizes, childWeights);
    }


    /*
     * Getters and Setters:
     */

    public int getNumInputs() {
        return numInputs;
    }

    public int[] getLayerSizes() {
        return layerSizes;
    }

    public List<Float> getWeights() {
        List<Float> list = new ArrayList<>(weights.length);

        for (float weight : weights) {
            list.add(weight);
        }

        return list;
    }
}