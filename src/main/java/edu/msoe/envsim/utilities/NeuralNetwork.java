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
     * Specialized unrolled forward pass for the 69 -> 16 -> 4 agent brain.
     * Eliminates all loop overhead, conditional branches, and array lookups.
     */
//    public float[] predict69x16x4(float @NotNull [] in) {
//        float[] w = weights;
//        float[] h = layerBuffers[1];   // size 16 hidden layer
//        float[] out = layerBuffers[2]; // size 4 output layer
//
//        // --- Hidden Layer (16 neurons, 69 inputs + bias = 70 weights each) ---
//        h[0]  = Math.max(w[0]   + w[1]*in[0]   + w[2]*in[1]   + w[3]*in[2]   + w[4]*in[3]   + w[5]*in[4]   + w[6]*in[5]   + w[7]*in[6]   + w[8]*in[7]   + w[9]*in[8]   + w[10]*in[9]  + w[11]*in[10]  + w[12]*in[11]  + w[13]*in[12]  + w[14]*in[13]  + w[15]*in[14]  + w[16]*in[15]  + w[17]*in[16]  + w[18]*in[17]  + w[19]*in[18]  + w[20]*in[19]  + w[21]*in[20]  + w[22]*in[21]  + w[23]*in[22]  + w[24]*in[23]  + w[25]*in[24]  + w[26]*in[25]  + w[27]*in[26]  + w[28]*in[27]  + w[29]*in[28]  + w[30]*in[29]  + w[31]*in[30]  + w[32]*in[31]  + w[33]*in[32]  + w[34]*in[33]  + w[35]*in[34]  + w[36]*in[35]  + w[37]*in[36]  + w[38]*in[37]  + w[39]*in[38]  + w[40]*in[39]  + w[41]*in[40]  + w[42]*in[41]  + w[43]*in[42]  + w[44]*in[43]  + w[45]*in[44]  + w[46]*in[45]  + w[47]*in[46]  + w[48]*in[47]  + w[49]*in[48]  + w[50]*in[49]  + w[51]*in[50]  + w[52]*in[51]  + w[53]*in[52]  + w[54]*in[53]  + w[55]*in[54]  + w[56]*in[55]  + w[57]*in[56]  + w[58]*in[57]  + w[59]*in[58]  + w[60]*in[59]  + w[61]*in[60]  + w[62]*in[61]  + w[63]*in[62]  + w[64]*in[63]  + w[65]*in[64]  + w[66]*in[65]  + w[67]*in[66]  + w[68]*in[67]  + w[69]*in[68], 0f);
//        h[1]  = Math.max(w[70]  + w[71]*in[0]  + w[72]*in[1]  + w[73]*in[2]  + w[74]*in[3]  + w[75]*in[4]  + w[76]*in[5]  + w[77]*in[6]  + w[78]*in[7]  + w[79]*in[8]  + w[80]*in[9]  + w[81]*in[10]  + w[82]*in[11]  + w[83]*in[12]  + w[84]*in[13]  + w[85]*in[14]  + w[86]*in[15]  + w[87]*in[16]  + w[88]*in[17]  + w[89]*in[18]  + w[90]*in[19]  + w[91]*in[20]  + w[92]*in[21]  + w[93]*in[22]  + w[94]*in[23]  + w[95]*in[24]  + w[96]*in[25]  + w[97]*in[26]  + w[98]*in[27]  + w[99]*in[28]  + w[100]*in[29] + w[101]*in[30] + w[102]*in[31] + w[103]*in[32] + w[104]*in[33] + w[105]*in[34] + w[106]*in[35] + w[107]*in[36] + w[108]*in[37] + w[109]*in[38] + w[110]*in[39] + w[111]*in[40] + w[112]*in[41] + w[113]*in[42] + w[114]*in[43] + w[115]*in[44] + w[116]*in[45] + w[117]*in[46] + w[118]*in[47] + w[119]*in[48] + w[120]*in[49] + w[121]*in[50] + w[122]*in[51] + w[123]*in[52] + w[124]*in[53] + w[125]*in[54] + w[126]*in[55] + w[127]*in[56] + w[128]*in[57] + w[129]*in[58] + w[130]*in[59] + w[131]*in[60] + w[132]*in[61] + w[133]*in[62] + w[134]*in[63] + w[135]*in[64] + w[136]*in[65] + w[137]*in[66] + w[138]*in[67] + w[139]*in[68], 0f);
//        h[2]  = Math.max(w[140] + w[141]*in[0] + w[142]*in[1] + w[143]*in[2] + w[144]*in[3] + w[145]*in[4] + w[146]*in[5] + w[147]*in[6] + w[148]*in[7] + w[149]*in[8] + w[150]*in[9] + w[151]*in[10] + w[152]*in[11] + w[153]*in[12] + w[154]*in[13] + w[155]*in[14] + w[156]*in[15] + w[157]*in[16] + w[158]*in[17] + w[159]*in[18] + w[160]*in[19] + w[161]*in[20] + w[162]*in[21] + w[163]*in[22] + w[164]*in[23] + w[165]*in[24] + w[166]*in[25] + w[167]*in[26] + w[168]*in[27] + w[169]*in[28] + w[170]*in[29] + w[171]*in[30] + w[172]*in[31] + w[173]*in[32] + w[174]*in[33] + w[175]*in[34] + w[176]*in[35] + w[177]*in[36] + w[178]*in[37] + w[179]*in[38] + w[180]*in[39] + w[181]*in[40] + w[182]*in[41] + w[183]*in[42] + w[184]*in[43] + w[185]*in[44] + w[186]*in[45] + w[187]*in[46] + w[188]*in[47] + w[189]*in[48] + w[190]*in[49] + w[191]*in[50] + w[192]*in[51] + w[193]*in[52] + w[194]*in[53] + w[195]*in[54] + w[196]*in[55] + w[197]*in[56] + w[198]*in[57] + w[199]*in[58] + w[200]*in[59] + w[201]*in[60] + w[202]*in[61] + w[203]*in[62] + w[204]*in[63] + w[205]*in[64] + w[206]*in[65] + w[207]*in[66] + w[208]*in[67] + w[209]*in[68], 0f);
//        h[3]  = Math.max(w[210] + w[211]*in[0] + w[212]*in[1] + w[213]*in[2] + w[214]*in[3] + w[215]*in[4] + w[216]*in[5] + w[217]*in[6] + w[218]*in[7] + w[219]*in[8] + w[220]*in[9] + w[221]*in[10] + w[222]*in[11] + w[223]*in[12] + w[224]*in[13] + w[225]*in[14] + w[226]*in[15] + w[227]*in[16] + w[228]*in[17] + w[229]*in[18] + w[230]*in[19] + w[231]*in[20] + w[232]*in[21] + w[233]*in[22] + w[234]*in[23] + w[235]*in[24] + w[236]*in[25] + w[237]*in[26] + w[238]*in[27] + w[239]*in[28] + w[240]*in[29] + w[241]*in[30] + w[242]*in[31] + w[243]*in[32] + w[244]*in[33] + w[245]*in[34] + w[246]*in[35] + w[247]*in[36] + w[248]*in[37] + w[249]*in[38] + w[250]*in[39] + w[251]*in[40] + w[252]*in[41] + w[253]*in[42] + w[254]*in[43] + w[255]*in[44] + w[256]*in[45] + w[257]*in[46] + w[258]*in[47] + w[259]*in[48] + w[260]*in[49] + w[261]*in[50] + w[262]*in[51] + w[263]*in[52] + w[264]*in[53] + w[265]*in[54] + w[266]*in[55] + w[267]*in[56] + w[268]*in[57] + w[269]*in[58] + w[270]*in[59] + w[271]*in[60] + w[272]*in[61] + w[273]*in[62] + w[274]*in[63] + w[275]*in[64] + w[276]*in[65] + w[277]*in[66] + w[278]*in[67] + w[279]*in[68], 0f);
//        h[4]  = Math.max(w[280] + w[281]*in[0] + w[282]*in[1] + w[283]*in[2] + w[284]*in[3] + w[285]*in[4] + w[286]*in[5] + w[287]*in[6] + w[288]*in[7] + w[289]*in[8] + w[290]*in[9] + w[291]*in[10] + w[292]*in[11] + w[293]*in[12] + w[294]*in[13] + w[295]*in[14] + w[296]*in[15] + w[297]*in[16] + w[298]*in[17] + w[299]*in[18] + w[300]*in[19] + w[301]*in[20] + w[302]*in[21] + w[303]*in[22] + w[304]*in[23] + w[305]*in[24] + w[306]*in[25] + w[307]*in[26] + w[308]*in[27] + w[309]*in[28] + w[310]*in[29] + w[311]*in[30] + w[312]*in[31] + w[313]*in[32] + w[314]*in[33] + w[315]*in[34] + w[316]*in[35] + w[317]*in[36] + w[318]*in[37] + w[319]*in[38] + w[320]*in[39] + w[321]*in[40] + w[322]*in[41] + w[323]*in[42] + w[324]*in[43] + w[325]*in[44] + w[326]*in[45] + w[327]*in[46] + w[328]*in[47] + w[329]*in[48] + w[330]*in[49] + w[331]*in[50] + w[332]*in[51] + w[333]*in[52] + w[334]*in[53] + w[335]*in[54] + w[336]*in[55] + w[337]*in[56] + w[338]*in[57] + w[339]*in[58] + w[340]*in[59] + w[341]*in[60] + w[342]*in[61] + w[343]*in[62] + w[344]*in[63] + w[345]*in[64] + w[346]*in[65] + w[347]*in[66] + w[348]*in[67] + w[349]*in[68], 0f);
//        h[5]  = Math.max(w[350] + w[351]*in[0] + w[352]*in[1] + w[353]*in[2] + w[354]*in[3] + w[355]*in[4] + w[356]*in[5] + w[357]*in[6] + w[358]*in[7] + w[359]*in[8] + w[360]*in[9] + w[361]*in[10] + w[362]*in[11] + w[363]*in[12] + w[364]*in[13] + w[365]*in[14] + w[366]*in[15] + w[367]*in[16] + w[368]*in[17] + w[369]*in[18] + w[370]*in[19] + w[371]*in[20] + w[372]*in[21] + w[373]*in[22] + w[374]*in[23] + w[375]*in[24] + w[376]*in[25] + w[377]*in[26] + w[378]*in[27] + w[379]*in[28] + w[380]*in[29] + w[381]*in[30] + w[382]*in[31] + w[383]*in[32] + w[384]*in[33] + w[385]*in[34] + w[386]*in[35] + w[387]*in[36] + w[388]*in[37] + w[389]*in[38] + w[390]*in[39] + w[391]*in[40] + w[392]*in[41] + w[393]*in[42] + w[394]*in[43] + w[395]*in[44] + w[396]*in[45] + w[397]*in[46] + w[398]*in[47] + w[399]*in[48] + w[400]*in[49] + w[401]*in[50] + w[402]*in[51] + w[403]*in[52] + w[404]*in[53] + w[405]*in[54] + w[406]*in[55] + w[407]*in[56] + w[408]*in[57] + w[409]*in[58] + w[410]*in[59] + w[411]*in[60] + w[412]*in[61] + w[413]*in[62] + w[414]*in[63] + w[415]*in[64] + w[416]*in[65] + w[417]*in[66] + w[418]*in[67] + w[419]*in[68], 0f);
//        h[6]  = Math.max(w[420] + w[421]*in[0] + w[422]*in[1] + w[423]*in[2] + w[424]*in[3] + w[425]*in[4] + w[426]*in[5] + w[427]*in[6] + w[428]*in[7] + w[429]*in[8] + w[430]*in[9] + w[431]*in[10] + w[432]*in[11] + w[433]*in[12] + w[434]*in[13] + w[435]*in[14] + w[436]*in[15] + w[437]*in[16] + w[438]*in[17] + w[439]*in[18] + w[440]*in[19] + w[441]*in[20] + w[442]*in[21] + w[443]*in[22] + w[444]*in[23] + w[445]*in[24] + w[446]*in[25] + w[447]*in[26] + w[448]*in[27] + w[449]*in[28] + w[450]*in[29] + w[451]*in[30] + w[452]*in[31] + w[453]*in[32] + w[454]*in[33] + w[455]*in[34] + w[456]*in[35] + w[457]*in[36] + w[458]*in[37] + w[459]*in[38] + w[460]*in[39] + w[461]*in[40] + w[462]*in[41] + w[463]*in[42] + w[464]*in[43] + w[465]*in[44] + w[466]*in[45] + w[467]*in[46] + w[468]*in[47] + w[469]*in[48] + w[470]*in[49] + w[471]*in[50] + w[472]*in[51] + w[473]*in[52] + w[474]*in[53] + w[475]*in[54] + w[476]*in[55] + w[477]*in[56] + w[478]*in[57] + w[479]*in[58] + w[480]*in[59] + w[481]*in[60] + w[482]*in[61] + w[483]*in[62] + w[484]*in[63] + w[485]*in[64] + w[486]*in[65] + w[487]*in[66] + w[488]*in[67] + w[489]*in[68], 0f);
//        h[7]  = Math.max(w[490] + w[491]*in[0] + w[492]*in[1] + w[493]*in[2] + w[494]*in[3] + w[495]*in[4] + w[496]*in[5] + w[497]*in[6] + w[498]*in[7] + w[499]*in[8] + w[500]*in[9] + w[501]*in[10] + w[502]*in[11] + w[503]*in[12] + w[504]*in[13] + w[505]*in[14] + w[506]*in[15] + w[507]*in[16] + w[508]*in[17] + w[509]*in[18] + w[510]*in[19] + w[511]*in[20] + w[512]*in[21] + w[513]*in[22] + w[514]*in[23] + w[515]*in[24] + w[516]*in[25] + w[517]*in[26] + w[518]*in[27] + w[519]*in[28] + w[520]*in[29] + w[521]*in[30] + w[522]*in[31] + w[523]*in[32] + w[524]*in[33] + w[525]*in[34] + w[526]*in[35] + w[527]*in[36] + w[528]*in[37] + w[529]*in[38] + w[530]*in[39] + w[531]*in[40] + w[532]*in[41] + w[533]*in[42] + w[534]*in[43] + w[535]*in[44] + w[536]*in[45] + w[537]*in[46] + w[538]*in[47] + w[539]*in[48] + w[540]*in[49] + w[541]*in[50] + w[542]*in[51] + w[543]*in[52] + w[544]*in[53] + w[545]*in[54] + w[546]*in[55] + w[547]*in[56] + w[548]*in[57] + w[549]*in[58] + w[550]*in[59] + w[551]*in[60] + w[552]*in[61] + w[553]*in[62] + w[554]*in[63] + w[555]*in[64] + w[556]*in[65] + w[557]*in[66] + w[558]*in[67] + w[559]*in[68], 0f);
//        h[8]  = Math.max(w[560] + w[561]*in[0] + w[562]*in[1] + w[563]*in[2] + w[564]*in[3] + w[565]*in[4] + w[566]*in[5] + w[567]*in[6] + w[568]*in[7] + w[569]*in[8] + w[570]*in[9] + w[571]*in[10] + w[572]*in[11] + w[573]*in[12] + w[574]*in[13] + w[575]*in[14] + w[576]*in[15] + w[577]*in[16] + w[578]*in[17] + w[579]*in[18] + w[580]*in[19] + w[581]*in[20] + w[582]*in[21] + w[583]*in[22] + w[584]*in[23] + w[585]*in[24] + w[586]*in[25] + w[587]*in[26] + w[588]*in[27] + w[589]*in[28] + w[590]*in[29] + w[591]*in[30] + w[592]*in[31] + w[593]*in[32] + w[594]*in[33] + w[595]*in[34] + w[596]*in[35] + w[597]*in[36] + w[598]*in[37] + w[599]*in[38] + w[600]*in[39] + w[601]*in[40] + w[602]*in[41] + w[603]*in[42] + w[604]*in[43] + w[605]*in[44] + w[606]*in[45] + w[607]*in[46] + w[608]*in[47] + w[609]*in[48] + w[610]*in[49] + w[611]*in[50] + w[612]*in[51] + w[613]*in[52] + w[614]*in[53] + w[615]*in[54] + w[616]*in[55] + w[617]*in[56] + w[618]*in[57] + w[619]*in[58] + w[620]*in[59] + w[621]*in[60] + w[622]*in[61] + w[623]*in[62] + w[624]*in[63] + w[625]*in[64] + w[626]*in[65] + w[627]*in[66] + w[628]*in[67] + w[629]*in[68], 0f);
//        h[9]  = Math.max(w[630] + w[631]*in[0] + w[632]*in[1] + w[633]*in[2] + w[634]*in[3] + w[635]*in[4] + w[636]*in[5] + w[637]*in[6] + w[638]*in[7] + w[639]*in[8] + w[640]*in[9] + w[641]*in[10] + w[642]*in[11] + w[643]*in[12] + w[644]*in[13] + w[645]*in[14] + w[646]*in[15] + w[647]*in[16] + w[648]*in[17] + w[649]*in[18] + w[650]*in[19] + w[651]*in[20] + w[652]*in[21] + w[653]*in[22] + w[654]*in[23] + w[655]*in[24] + w[656]*in[25] + w[657]*in[26] + w[658]*in[27] + w[659]*in[28] + w[660]*in[29] + w[661]*in[30] + w[662]*in[31] + w[663]*in[32] + w[664]*in[33] + w[665]*in[34] + w[666]*in[35] + w[667]*in[36] + w[668]*in[37] + w[669]*in[38] + w[670]*in[39] + w[671]*in[40] + w[672]*in[41] + w[673]*in[42] + w[674]*in[43] + w[675]*in[44] + w[676]*in[45] + w[677]*in[46] + w[678]*in[47] + w[679]*in[48] + w[680]*in[49] + w[681]*in[50] + w[682]*in[51] + w[683]*in[52] + w[684]*in[53] + w[685]*in[54] + w[686]*in[55] + w[687]*in[56] + w[688]*in[57] + w[689]*in[58] + w[690]*in[59] + w[691]*in[60] + w[692]*in[61] + w[693]*in[62] + w[694]*in[63] + w[695]*in[64] + w[696]*in[65] + w[697]*in[66] + w[698]*in[67] + w[699]*in[68], 0f);
//        h[10] = Math.max(w[700] + w[701]*in[0] + w[702]*in[1] + w[703]*in[2] + w[704]*in[3] + w[705]*in[4] + w[706]*in[5] + w[707]*in[6] + w[708]*in[7] + w[709]*in[8] + w[710]*in[9] + w[711]*in[10] + w[712]*in[11] + w[713]*in[12] + w[714]*in[13] + w[715]*in[14] + w[716]*in[15] + w[717]*in[16] + w[718]*in[17] + w[719]*in[18] + w[720]*in[19] + w[721]*in[20] + w[722]*in[21] + w[723]*in[22] + w[724]*in[23] + w[725]*in[24] + w[726]*in[25] + w[727]*in[26] + w[728]*in[27] + w[729]*in[28] + w[730]*in[29] + w[731]*in[30] + w[732]*in[31] + w[733]*in[32] + w[734]*in[33] + w[735]*in[34] + w[736]*in[35] + w[737]*in[36] + w[738]*in[37] + w[739]*in[38] + w[740]*in[39] + w[741]*in[40] + w[742]*in[41] + w[743]*in[42] + w[744]*in[43] + w[745]*in[44] + w[746]*in[45] + w[747]*in[46] + w[748]*in[47] + w[749]*in[48] + w[750]*in[49] + w[751]*in[50] + w[752]*in[51] + w[753]*in[52] + w[754]*in[53] + w[755]*in[54] + w[756]*in[55] + w[757]*in[56] + w[758]*in[57] + w[759]*in[58] + w[760]*in[59] + w[761]*in[60] + w[762]*in[61] + w[763]*in[62] + w[764]*in[63] + w[765]*in[64] + w[766]*in[65] + w[767]*in[66] + w[768]*in[67] + w[769]*in[68], 0f);
//        h[11] = Math.max(w[770] + w[771]*in[0] + w[772]*in[1] + w[773]*in[2] + w[774]*in[3] + w[775]*in[4] + w[776]*in[5] + w[777]*in[6] + w[778]*in[7] + w[779]*in[8] + w[780]*in[9] + w[781]*in[10] + w[782]*in[11] + w[783]*in[12] + w[784]*in[13] + w[785]*in[14] + w[786]*in[15] + w[787]*in[16] + w[788]*in[17] + w[789]*in[18] + w[790]*in[19] + w[791]*in[20] + w[792]*in[21] + w[793]*in[22] + w[794]*in[23] + w[795]*in[24] + w[796]*in[25] + w[797]*in[26] + w[798]*in[27] + w[799]*in[28] + w[800]*in[29] + w[801]*in[30] + w[802]*in[31] + w[803]*in[32] + w[804]*in[33] + w[805]*in[34] + w[806]*in[35] + w[807]*in[36] + w[808]*in[37] + w[809]*in[38] + w[810]*in[39] + w[811]*in[40] + w[812]*in[41] + w[813]*in[42] + w[814]*in[43] + w[815]*in[44] + w[816]*in[45] + w[817]*in[46] + w[818]*in[47] + w[819]*in[48] + w[820]*in[49] + w[821]*in[50] + w[822]*in[51] + w[823]*in[52] + w[824]*in[53] + w[825]*in[54] + w[826]*in[55] + w[827]*in[56] + w[828]*in[57] + w[829]*in[58] + w[830]*in[59] + w[831]*in[60] + w[832]*in[61] + w[833]*in[62] + w[834]*in[63] + w[835]*in[64] + w[836]*in[65] + w[837]*in[66] + w[838]*in[67] + w[839]*in[68], 0f);
//        h[12] = Math.max(w[840] + w[841]*in[0] + w[842]*in[1] + w[843]*in[2] + w[844]*in[3] + w[845]*in[4] + w[846]*in[5] + w[847]*in[6] + w[848]*in[7] + w[849]*in[8] + w[850]*in[9] + w[851]*in[10] + w[852]*in[11] + w[853]*in[12] + w[854]*in[13] + w[855]*in[14] + w[856]*in[15] + w[857]*in[16] + w[858]*in[17] + w[859]*in[18] + w[860]*in[19] + w[861]*in[20] + w[862]*in[21] + w[863]*in[22] + w[864]*in[23] + w[865]*in[24] + w[866]*in[25] + w[867]*in[26] + w[868]*in[27] + w[869]*in[28] + w[870]*in[29] + w[871]*in[30] + w[872]*in[31] + w[873]*in[32] + w[874]*in[33] + w[875]*in[34] + w[876]*in[35] + w[877]*in[36] + w[878]*in[37] + w[879]*in[38] + w[880]*in[39] + w[881]*in[40] + w[882]*in[41] + w[883]*in[42] + w[884]*in[43] + w[885]*in[44] + w[886]*in[45] + w[887]*in[46] + w[888]*in[47] + w[889]*in[48] + w[890]*in[49] + w[891]*in[50] + w[892]*in[51] + w[893]*in[52] + w[894]*in[53] + w[895]*in[54] + w[896]*in[55] + w[897]*in[56] + w[898]*in[57] + w[899]*in[58] + w[900]*in[59] + w[901]*in[60] + w[902]*in[61] + w[903]*in[62] + w[904]*in[63] + w[905]*in[64] + w[906]*in[65] + w[907]*in[66] + w[908]*in[67] + w[909]*in[68], 0f);
//        h[13] = Math.max(w[910] + w[911]*in[0] + w[912]*in[1] + w[913]*in[2] + w[914]*in[3] + w[915]*in[4] + w[916]*in[5] + w[917]*in[6] + w[918]*in[7] + w[919]*in[8] + w[920]*in[9] + w[921]*in[10] + w[922]*in[11] + w[923]*in[12] + w[924]*in[13] + w[925]*in[14] + w[926]*in[15] + w[927]*in[16] + w[928]*in[17] + w[929]*in[18] + w[930]*in[19] + w[931]*in[20] + w[932]*in[21] + w[933]*in[22] + w[934]*in[23] + w[935]*in[24] + w[936]*in[25] + w[937]*in[26] + w[938]*in[27] + w[939]*in[28] + w[940]*in[29] + w[941]*in[30] + w[942]*in[31] + w[943]*in[32] + w[944]*in[33] + w[945]*in[34] + w[946]*in[35] + w[947]*in[36] + w[948]*in[37] + w[949]*in[38] + w[950]*in[39] + w[951]*in[40] + w[952]*in[41] + w[953]*in[42] + w[954]*in[43] + w[955]*in[44] + w[956]*in[45] + w[957]*in[46] + w[958]*in[47] + w[959]*in[48] + w[960]*in[49] + w[961]*in[50] + w[962]*in[51] + w[963]*in[52] + w[964]*in[53] + w[965]*in[54] + w[966]*in[55] + w[967]*in[56] + w[968]*in[57] + w[969]*in[58] + w[970]*in[59] + w[971]*in[60] + w[972]*in[61] + w[973]*in[62] + w[974]*in[63] + w[975]*in[64] + w[976]*in[65] + w[977]*in[66] + w[978]*in[67] + w[979]*in[68], 0f);
//        h[14] = Math.max(w[980] + w[981]*in[0] + w[982]*in[1] + w[983]*in[2] + w[984]*in[3] + w[985]*in[4] + w[986]*in[5] + w[987]*in[6] + w[988]*in[7] + w[989]*in[8] + w[990]*in[9] + w[991]*in[10] + w[992]*in[11] + w[993]*in[12] + w[994]*in[13] + w[995]*in[14] + w[996]*in[15] + w[997]*in[16] + w[998]*in[17] + w[999]*in[18] + w[1000]*in[19] + w[1001]*in[20] + w[1002]*in[21] + w[1003]*in[22] + w[1004]*in[23] + w[1005]*in[24] + w[1006]*in[25] + w[1007]*in[26] + w[1008]*in[27] + w[1009]*in[28] + w[1010]*in[29] + w[1011]*in[30] + w[1012]*in[31] + w[1013]*in[32] + w[1014]*in[33] + w[1015]*in[34] + w[1016]*in[35] + w[1017]*in[36] + w[1018]*in[37] + w[1019]*in[38] + w[1020]*in[39] + w[1021]*in[40] + w[1022]*in[41] + w[1023]*in[42] + w[1024]*in[43] + w[1025]*in[44] + w[1026]*in[45] + w[1027]*in[46] + w[1028]*in[47] + w[1029]*in[48] + w[1030]*in[49] + w[1031]*in[50] + w[1032]*in[51] + w[1033]*in[52] + w[1034]*in[53] + w[1035]*in[54] + w[1036]*in[55] + w[1037]*in[56] + w[1038]*in[57] + w[1039]*in[58] + w[1040]*in[59] + w[1041]*in[60] + w[1042]*in[61] + w[1043]*in[62] + w[1044]*in[63] + w[1045]*in[64] + w[1046]*in[65] + w[1047]*in[66] + w[1048]*in[67] + w[1049]*in[68], 0f);
//        h[15] = Math.max(w[1050] + w[1051]*in[0] + w[1052]*in[1] + w[1053]*in[2] + w[1054]*in[3] + w[1055]*in[4] + w[1056]*in[5] + w[1057]*in[6] + w[1058]*in[7] + w[1059]*in[8] + w[1060]*in[9] + w[1061]*in[10] + w[1062]*in[11] + w[1063]*in[12] + w[1064]*in[13] + w[1065]*in[14] + w[1066]*in[15] + w[1067]*in[16] + w[1068]*in[17] + w[1069]*in[18] + w[1070]*in[19] + w[1071]*in[20] + w[1072]*in[21] + w[1073]*in[22] + w[1074]*in[23] + w[1075]*in[24] + w[1076]*in[25] + w[1077]*in[26] + w[1078]*in[27] + w[1079]*in[28] + w[1080]*in[29] + w[1081]*in[30] + w[1082]*in[31] + w[1083]*in[32] + w[1084]*in[33] + w[1085]*in[34] + w[1086]*in[35] + w[1087]*in[36] + w[1088]*in[37] + w[1089]*in[38] + w[1090]*in[39] + w[1091]*in[40] + w[1092]*in[41] + w[1093]*in[42] + w[1094]*in[43] + w[1095]*in[44] + w[1096]*in[45] + w[1097]*in[46] + w[1098]*in[47] + w[1099]*in[48] + w[1100]*in[49] + w[1101]*in[50] + w[1102]*in[51] + w[1103]*in[52] + w[1104]*in[53] + w[1105]*in[54] + w[1106]*in[55] + w[1107]*in[56] + w[1108]*in[57] + w[1109]*in[58] + w[1110]*in[59] + w[1111]*in[60] + w[1112]*in[61] + w[1113]*in[62] + w[1114]*in[63] + w[1115]*in[64] + w[1116]*in[65] + w[1117]*in[66] + w[1118]*in[67] + w[1119]*in[68], 0f);
//
//        // --- Output Layer (4 neurons, 16 hidden inputs + bias = 17 weights each) ---
//        int ow = 1120;
//        out[0] = fastSigmoid(w[ow]    + w[ow+1]*h[0]  + w[ow+2]*h[1]  + w[ow+3]*h[2]  + w[ow+4]*h[3]  + w[ow+5]*h[4]  + w[ow+6]*h[5]  + w[ow+7]*h[6]  + w[ow+8]*h[7]  + w[ow+9]*h[8]  + w[ow+10]*h[9] + w[ow+11]*h[10] + w[ow+12]*h[11] + w[ow+13]*h[12] + w[ow+14]*h[13] + w[ow+15]*h[14] + w[ow+16]*h[15]);
//        out[1] = fastSigmoid(w[ow+17] + w[ow+18]*h[0] + w[ow+19]*h[1] + w[ow+20]*h[2] + w[ow+21]*h[3] + w[ow+22]*h[4] + w[ow+23]*h[5] + w[ow+24]*h[6] + w[ow+25]*h[7] + w[ow+26]*h[8] + w[ow+27]*h[9] + w[ow+28]*h[10] + w[ow+29]*h[11] + w[ow+30]*h[12] + w[ow+31]*h[13] + w[ow+32]*h[14] + w[ow+33]*h[15]);
//        out[2] = fastSigmoid(w[ow+34] + w[ow+35]*h[0] + w[ow+36]*h[1] + w[ow+37]*h[2] + w[ow+38]*h[3] + w[ow+39]*h[4] + w[ow+40]*h[5] + w[ow+41]*h[6] + w[ow+42]*h[7] + w[ow+43]*h[8] + w[ow+44]*h[9] + w[ow+45]*h[10] + w[ow+46]*h[11] + w[ow+47]*h[12] + w[ow+48]*h[13] + w[ow+49]*h[14] + w[ow+50]*h[15]);
//        out[3] = fastSigmoid(w[ow+51] + w[ow+52]*h[0] + w[ow+53]*h[1] + w[ow+54]*h[2] + w[ow+55]*h[3] + w[ow+56]*h[4] + w[ow+57]*h[5] + w[ow+58]*h[6] + w[ow+59]*h[7] + w[ow+60]*h[8] + w[ow+61]*h[9] + w[ow+62]*h[10] + w[ow+63]*h[11] + w[ow+64]*h[12] + w[ow+65]*h[13] + w[ow+66]*h[14] + w[ow+67]*h[15]);
//
//        return out;
//    }

    /**
     * Optimized forward pass for the 69 -> 16 -> 4 agent brain.
     * Uses clean loops that the JVM JIT compiler can easily auto-vectorize (SIMD)
     * and fully inline, avoiding instruction cache thrashing.
     */
    public float[] predict69x16x4(float @NotNull [] in) {
        float[] w = weights;
        float[] h = layerBuffers[1];   // size 16 hidden layer
        float[] out = layerBuffers[2]; // size 4 output layer

        int wIdx = 0;

        // --- Hidden Layer (16 neurons, 69 inputs + bias = 70 weights each) ---
        for (int j = 0; j < 16; j++) {
            float sum = w[wIdx++]; // Bias
            for (int k = 0; k < 69; k++) {
                sum += w[wIdx++] * in[k];
            }
            h[j] = Math.max(sum, 0f); // ReLU activation
        }

        // --- Output Layer (4 neurons, 16 hidden inputs + bias = 17 weights each) ---
        for (int j = 0; j < 4; j++) {
            float sum = w[wIdx++]; // Bias
            for (int k = 0; k < 16; k++) {
                sum += w[wIdx++] * h[k];
            }
            out[j] = fastSigmoid(sum);
        }

        return out;
    }

    public static float fastSigmoid(double x) {
        return (float) (0.5f + (x / (2.0f * (1.0f + Math.abs(x)))));
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

    public void save(@NotNull java.io.DataOutputStream out) throws java.io.IOException {
        out.writeInt(layerSizes.length);
        for (int size : layerSizes) {
            out.writeInt(size);
        }
        out.writeInt(weights.length);
        for (float weight : weights) {
            out.writeFloat(weight);
        }
    }

    public static @NotNull NeuralNetwork load(@NotNull java.io.DataInputStream in) throws java.io.IOException {
        int length = in.readInt();
        int[] layerSizes = new int[length];
        for (int i = 0; i < length; i++) {
            layerSizes[i] = in.readInt();
        }
        int weightsLength = in.readInt();
        float[] weights = new float[weightsLength];
        for (int i = 0; i < weightsLength; i++) {
            weights[i] = in.readFloat();
        }
        return new NeuralNetwork(layerSizes, weights);
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