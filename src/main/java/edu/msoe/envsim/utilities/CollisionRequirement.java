package edu.msoe.envsim.utilities;

import edu.msoe.envsim.entities.Entity;

public interface CollisionRequirement {
    boolean checkCollision(Entity entity1, Entity entity2);
}
