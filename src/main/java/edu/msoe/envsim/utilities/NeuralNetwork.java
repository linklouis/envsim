package edu.msoe.envsim.utilities;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
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
            weights[i] = (float) (Math.random() * 2 - 1);
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

    /**
     * Source: Claude
     * @param a The first input value
     * @param b The second input value
     * @param c The third input value
     * @return The predicted value
     */
    public float predict3x3x1(float a, float b, float c) {
        float[] w = weights;
        float h0 = Math.max(w[0]  + w[1]*a  + w[2]*b  + w[3]*c, 0f);
        float h1 = Math.max(w[4]  + w[5]*a  + w[6]*b  + w[7]*c, 0f);
        float h2 = Math.max(w[8]  + w[9]*a  + w[10]*b + w[11]*c, 0f);
        float s  = w[12] + w[13]*h0 + w[14]*h1 + w[15]*h2;
        return (float) (1.0 / (1.0 + Math.exp(-s)));
    }

    public float predict4x3x1(float a, float b, float c, float d) {
        float[] w = weights;
        float h0 = Math.max(w[0]  + w[1]*a  + w[2]*b  + w[3]*c + w[4]*d, 0f);
        float h1 = Math.max(w[5]  + w[6]*a  + w[7]*b  + w[8]*c + w[9]*d, 0f);
        float h2 = Math.max(w[10]  + w[11]*a  + w[12]*b + w[13]*c + w[14]*d, 0f);
        float s  = w[15] + w[16]*h0 + w[17]*h1 + w[18]*h2;
        return (float) (1.0 / (1.0 + Math.exp(-s)));
    }

    public float predict6x3x1(float a, float b, float c, float d, float e, float f) {
        float[] w = weights;
        float h0 = Math.max(w[0]  + w[1]*a  + w[2]*b  + w[3]*c  + w[4]*d  + w[5]*e  + w[6]*f, 0f);
        float h1 = Math.max(w[7]  + w[8]*a  + w[9]*b  + w[10]*c + w[11]*d + w[12]*e + w[13]*f, 0f);
        float h2 = Math.max(w[14] + w[15]*a + w[16]*b + w[17]*c + w[18]*d + w[19]*e + w[20]*f, 0f);
        float s  = w[21] + w[22]*h0 + w[23]*h1 + w[24]*h2;
        return (float) (1.0 / (1.0 + Math.exp(-s)));
    }

    public float predict7x3x1(float a, float b, float c, float d, float e, float f, float g) {
        float[] w = weights;
        float h0 = Math.max(w[0]  + w[1]*a  + w[2]*b  + w[3]*c  + w[4]*d  + w[5]*e  + w[6]*f + w[7]*g, 0f);
        float h1 = Math.max(w[8]  + w[9]*a  + w[10]*b  + w[11]*c + w[12]*d + w[13]*e + w[14]*f + w[15]*g, 0f);
        float h2 = Math.max(w[16] + w[17]*a + w[18]*b + w[19]*c + w[20]*d + w[21]*e + w[22]*f + w[23]*f, 0f);
        float s  = w[24] + w[25]*h0 + w[25]*h1 + w[27]*h2;
        return (float) (1.0 / (1.0 + Math.exp(-s)));
    }

    /**
     * Specialized unrolled forward pass for the 6 -> 10 -> 7 agent brain.
     * Eliminates all loop overhead and array lookups.
     * Source: Google Gemini
     */
    public float[] predict6x10x7(float i0, float i1, float i2, float i3, float i4, float i5) {
        float[] w = weights;
        float[] h = layerBuffers[1]; // size 10 hidden layer
        float[] out = layerBuffers[2]; // size 7 output layer

        // --- Hidden Layer (10 neurons, 6 inputs + bias = 7 weights each) ---
        h[0] = Math.max(w[0]  + w[1]*i0  + w[2]*i1  + w[3]*i2  + w[4]*i3  + w[5]*i4  + w[6]*i5, 0f);
        h[1] = Math.max(w[7]  + w[8]*i0  + w[9]*i1  + w[10]*i2 + w[11]*i3 + w[12]*i4 + w[13]*i5, 0f);
        h[2] = Math.max(w[14] + w[15]*i0 + w[16]*i1 + w[17]*i2 + w[18]*i3 + w[19]*i4 + w[20]*i5, 0f);
        h[3] = Math.max(w[21] + w[22]*i0 + w[23]*i1 + w[24]*i2 + w[25]*i3 + w[26]*i4 + w[27]*i5, 0f);
        h[4] = Math.max(w[28] + w[29]*i0 + w[30]*i1 + w[31]*i2 + w[32]*i3 + w[33]*i4 + w[34]*i5, 0f);
        h[5] = Math.max(w[35] + w[36]*i0 + w[37]*i1 + w[38]*i2 + w[39]*i3 + w[40]*i4 + w[41]*i5, 0f);
        h[6] = Math.max(w[42] + w[43]*i0 + w[44]*i1 + w[45]*i2 + w[46]*i3 + w[47]*i4 + w[48]*i5, 0f);
        h[7] = Math.max(w[49] + w[50]*i0 + w[51]*i1 + w[52]*i2 + w[53]*i3 + w[54]*i4 + w[55]*i5, 0f);
        h[8] = Math.max(w[56] + w[57]*i0 + w[58]*i1 + w[59]*i2 + w[60]*i3 + w[61]*i4 + w[62]*i5, 0f);
        h[9] = Math.max(w[63] + w[64]*i0 + w[65]*i1 + w[66]*i2 + w[67]*i3 + w[68]*i4 + w[69]*i5, 0f);

        // --- Output Layer (7 neurons, 10 hidden inputs + bias = 11 weights each) ---
        // Starting index after hidden weights = 70
        out[0] = (float) (1.0 / (1.0 + Math.exp(-(w[70]  + w[71]*h[0]  + w[72]*h[1]  + w[73]*h[2]  + w[74]*h[3]  + w[75]*h[4]  + w[76]*h[5]  + w[77]*h[6]  + w[78]*h[7]  + w[79]*h[8]  + w[80]*h[9]))));
        out[1] = (float) (1.0 / (1.0 + Math.exp(-(w[81]  + w[82]*h[0]  + w[83]*h[1]  + w[84]*h[2]  + w[85]*h[3]  + w[86]*h[4]  + w[87]*h[5]  + w[88]*h[6]  + w[89]*h[7]  + w[90]*h[8]  + w[91]*h[9]))));
        out[2] = (float) (1.0 / (1.0 + Math.exp(-(w[92]  + w[93]*h[0]  + w[94]*h[1]  + w[95]*h[2]  + w[96]*h[3]  + w[97]*h[4]  + w[98]*h[5]  + w[99]*h[6]  + w[100]*h[7] + w[101]*h[8] + w[102]*h[9]))));
        out[3] = (float) (1.0 / (1.0 + Math.exp(-(w[103] + w[104]*h[0] + w[105]*h[1] + w[106]*h[2] + w[107]*h[3] + w[108]*h[4] + w[109]*h[5] + w[110]*h[6] + w[111]*h[7] + w[112]*h[8] + w[113]*h[9]))));
        out[4] = (float) (1.0 / (1.0 + Math.exp(-(w[114] + w[115]*h[0] + w[116]*h[1] + w[117]*h[2] + w[118]*h[3] + w[119]*h[4] + w[120]*h[5] + w[121]*h[6] + w[122]*h[7] + w[123]*h[8] + w[124]*h[9]))));
        out[5] = (float) (1.0 / (1.0 + Math.exp(-(w[125] + w[126]*h[0] + w[127]*h[1] + w[128]*h[2] + w[129]*h[3] + w[130]*h[4] + w[131]*h[5] + w[132]*h[6] + w[133]*h[7] + w[134]*h[8] + w[135]*h[9]))));
        out[6] = (float) (1.0 / (1.0 + Math.exp(-(w[136] + w[137]*h[0] + w[138]*h[1] + w[139]*h[2] + w[140]*h[3] + w[141]*h[4] + w[142]*h[5] + w[143]*h[6] + w[144]*h[7] + w[145]*h[8] + w[146]*h[9]))));

        return out;
    }

    /**
     * Specialized unrolled forward pass for the 9 -> 10 -> 5 agent brain.
     * Eliminates all loop overhead and array lookups.
     * Source: Google Gemini
     */
    public float[] predict9x10x5(float i0, float i1, float i2, float i3, float i4, float i5, float i6, float i7, float i8) {
        float[] w = weights;
        float[] h = layerBuffers[1]; // size 10 hidden layer
        float[] out = layerBuffers[2]; // size 5 output layer

        // --- Hidden Layer (10 neurons, 9 inputs + bias = 10 weights each) ---
        h[0] = Math.max(w[0]  + w[1]*i0  + w[2]*i1  + w[3]*i2  + w[4]*i3  + w[5]*i4  + w[6]*i5  + w[7]*i6  + w[8]*i7  + w[9]*i8, 0f);
        h[1] = Math.max(w[10] + w[11]*i0 + w[12]*i1 + w[13]*i2 + w[14]*i3 + w[15]*i4 + w[16]*i5 + w[17]*i6 + w[18]*i7 + w[19]*i8, 0f);
        h[2] = Math.max(w[20] + w[21]*i0 + w[22]*i1 + w[23]*i2 + w[24]*i3 + w[25]*i4 + w[26]*i5 + w[27]*i6 + w[28]*i7 + w[29]*i8, 0f);
        h[3] = Math.max(w[30] + w[31]*i0 + w[32]*i1 + w[33]*i2 + w[34]*i3 + w[35]*i4 + w[36]*i5 + w[37]*i6 + w[38]*i7 + w[39]*i8, 0f);
        h[4] = Math.max(w[40] + w[41]*i0 + w[42]*i1 + w[43]*i2 + w[44]*i3 + w[45]*i4 + w[46]*i5 + w[47]*i6 + w[48]*i7 + w[49]*i8, 0f);
        h[5] = Math.max(w[50] + w[51]*i0 + w[52]*i1 + w[53]*i2 + w[54]*i3 + w[55]*i4 + w[56]*i5 + w[57]*i6 + w[58]*i7 + w[59]*i8, 0f);
        h[6] = Math.max(w[60] + w[61]*i0 + w[62]*i1 + w[63]*i2 + w[64]*i3 + w[65]*i4 + w[66]*i5 + w[67]*i6 + w[68]*i7 + w[69]*i8, 0f);
        h[7] = Math.max(w[70] + w[71]*i0 + w[72]*i1 + w[73]*i2 + w[74]*i3 + w[75]*i4 + w[76]*i5 + w[77]*i6 + w[78]*i7 + w[79]*i8, 0f);
        h[8] = Math.max(w[80] + w[81]*i0 + w[82]*i1 + w[83]*i2 + w[84]*i3 + w[85]*i4 + w[86]*i5 + w[87]*i6 + w[88]*i7 + w[89]*i8, 0f);
        h[9] = Math.max(w[90] + w[91]*i0 + w[92]*i1 + w[93]*i2 + w[94]*i3 + w[95]*i4 + w[96]*i5 + w[97]*i6 + w[98]*i7 + w[99]*i8, 0f);

        // --- Output Layer (5 neurons, 10 hidden inputs + bias = 11 weights each) ---
        // Starting index after hidden weights = 100
        out[0] = (float) (1.0 / (1.0 + Math.exp(-(w[100] + w[101]*h[0] + w[102]*h[1] + w[103]*h[2] + w[104]*h[3] + w[105]*h[4] + w[106]*h[5] + w[107]*h[6] + w[108]*h[7] + w[109]*h[8] + w[110]*h[9]))));
        out[1] = (float) (1.0 / (1.0 + Math.exp(-(w[111] + w[112]*h[0] + w[113]*h[1] + w[114]*h[2] + w[115]*h[3] + w[116]*h[4] + w[117]*h[5] + w[118]*h[6] + w[119]*h[7] + w[120]*h[8] + w[121]*h[9]))));
        out[2] = (float) (1.0 / (1.0 + Math.exp(-(w[122] + w[123]*h[0] + w[124]*h[1] + w[125]*h[2] + w[126]*h[3] + w[127]*h[4] + w[128]*h[5] + w[129]*h[6] + w[130]*h[7] + w[131]*h[8] + w[132]*h[9]))));
        out[3] = (float) (1.0 / (1.0 + Math.exp(-(w[133] + w[134]*h[0] + w[135]*h[1] + w[136]*h[2] + w[137]*h[3] + w[138]*h[4] + w[139]*h[5] + w[140]*h[6] + w[141]*h[7] + w[142]*h[8] + w[143]*h[9]))));
        out[4] = (float) (1.0 / (1.0 + Math.exp(-(w[144] + w[145]*h[0] + w[146]*h[1] + w[147]*h[2] + w[148]*h[3] + w[149]*h[4] + w[150]*h[5] + w[151]*h[6] + w[152]*h[7] + w[153]*h[8] + w[154]*h[9]))));

        return out;
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