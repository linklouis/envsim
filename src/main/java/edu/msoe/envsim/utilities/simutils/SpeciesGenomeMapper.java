package edu.msoe.envsim.utilities.simutils;

import org.jetbrains.annotations.NotNull;

public class SpeciesGenomeMapper extends DimensionalMapper {
    private static final int OUTPUT_SIZE = 150;
    private static final float MAX_RADIUS = 0.5f;

    /**
     * Projects a high-dimensional genome vector into a lower-dimensional representation.
     */
    public static float @NotNull [] getGenomeProjection(float @NotNull [] genome) {
        return getProjection(genome, OUTPUT_SIZE, MAX_RADIUS);
    }
}
