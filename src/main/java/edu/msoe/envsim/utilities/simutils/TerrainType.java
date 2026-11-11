package edu.msoe.envsim.utilities.simutils;

import javafx.scene.paint.Color;

import static edu.msoe.envsim.entities.Agent.AIR_DENSITY;
import static edu.msoe.envsim.entities.Agent.WATER_DENSITY;

public enum TerrainType {
    // Turning resistance has to be >= 1 because the division by radius already takes so much away from the turn.
    DESERT(new Color(0.65, 0.54, 0.30, 1), 1.2, 0.8, 0.01, 0.0, AIR_DENSITY, 0.5),
    STANDARD(new Color(0.1, 0.45, 0.2, 1), 1.5, 0.9, 0.01, 0.0, AIR_DENSITY, 1.0),
    MARSH(new Color(0.22, 0.28, 0.14, 1), 1.8, 1.0, 0.08, 0.1, AIR_DENSITY, 1.3),
    WATER(new Color(0.004, 0.329, 0.6, 1), 1.0, 0.0, 0.0, 1.0, WATER_DENSITY, 1.1);

    // TODO: Maybe add a variable for a modifier for how valuable food is in an area? Swamps can be more valuable, deserts can be less?
    private final Color color;
    private final double turningResistance;
    /**
     * The portion of sideways velocity removed each tick by contact friction with the ground.
     * 1 = perfect traction, so the agent can only move the way it faces. Water has no traction.
     */
    private final double lateralGrip;
    private final double walkingResistance;
    private final double swimSkillRequired;
    private final double fluidDensity;
    private final double foodValue;

    TerrainType(Color color, double turningResistance, double lateralGrip, double walkingResistance, double swimSkillRequired, double fluidDensity, double foodValue) {
        this.color = color;
        this.turningResistance = turningResistance;
        this.lateralGrip = lateralGrip;
        this.walkingResistance = walkingResistance;
        this.swimSkillRequired = swimSkillRequired;
        this.fluidDensity = fluidDensity;
        this.foodValue = foodValue;
    }

    public Color getColor() {
        return color;
    }

    public double getTurningResistance() {
        return turningResistance;
    }

    public double getLateralGrip() {
        return lateralGrip;
    }

    public double getWalkingResistance() {
        return walkingResistance;
    }

    public double getSwimSkillRequired() {
        return swimSkillRequired;
    }

    public double getFluidDensity() {
        return fluidDensity;
    }

    public double getFoodValue() {
        return foodValue;
    }
}
