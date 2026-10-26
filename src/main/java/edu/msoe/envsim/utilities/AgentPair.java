package edu.msoe.envsim.utilities;

import edu.msoe.envsim.entities.Agent;
import org.jetbrains.annotations.NotNull;

/**
 * Source: Gemini
 * @param agent1
 * @param agent2
 */
public record AgentPair(@NotNull Agent agent1, @NotNull Agent agent2) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentPair pair)) return false;
        // Order doesn't matter: A-B equals B-A
        return (agent1.equals(pair.agent1) && agent2.equals(pair.agent2)) ||
                (agent1.equals(pair.agent2) && agent2.equals(pair.agent1));
    }

    @Override
    public int hashCode() {
        // Symmetric hash code so A-B and B-A hash to the same bucket
        return agent1.hashCode() + agent2.hashCode();
    }
}