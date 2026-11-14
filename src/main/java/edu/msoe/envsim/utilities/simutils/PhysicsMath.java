package edu.msoe.envsim.utilities.simutils;

import static edu.msoe.envsim.entities.Agent.MAX_BODY_ELONGATION;

public interface PhysicsMath {
    /**
     * 20: 1px = 0.05m
     */
    double PIXELS_PER_METER = 50;
    /**
     * TODO: The density constants make more sense in TerrainType, but there's no good way to include them there.
     */
    double AIR_DENSITY = 1.225 / Math.pow(PIXELS_PER_METER, 3);
    /**
     * Also used as the average mass of an Agent's body per pixel
     */
    double WATER_DENSITY = 1025 / Math.pow(PIXELS_PER_METER, 3);
    double MAX_SIDEWAYS_DRAG = 0.96;
    double MIN_BLUNT_BODY_PORTION = calculatePortionOfBodyThatIsBlunt(MAX_BODY_ELONGATION);
    double SPHERE_DRAG_COEFFICIENT = 0.47;

    static double calculateForwardDragCoefficient(double bodyElongation) {
        return SPHERE_DRAG_COEFFICIENT / Math.sqrt(bodyElongation);
    }

    static double calculatePortionOfBodyThatIsBlunt(double bodyElongation) {
        return (bodyElongation - 1.0) / (bodyElongation + 1.0);
    }

    static double calculateSidewaysDragCoefficient(double bodyElongation) {
        double portionOfBodyThatIsBlunt = calculatePortionOfBodyThatIsBlunt(bodyElongation);
        return MAX_SIDEWAYS_DRAG * (portionOfBodyThatIsBlunt + 1) / (MIN_BLUNT_BODY_PORTION + 1);
    }
}
