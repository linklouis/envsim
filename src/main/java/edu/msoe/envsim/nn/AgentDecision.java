package edu.msoe.envsim.nn;

import edu.msoe.envsim.utilities.Vector;

public record AgentDecision(double speed, Vector direction, double turn, boolean desiresPredation) {
}
