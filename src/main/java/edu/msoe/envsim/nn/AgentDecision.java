package edu.msoe.envsim.nn;

import edu.msoe.envsim.Vector;

public record AgentDecision(double speed, Vector direction, boolean desiresReproduction, boolean desiresPredation) {
}
