package edu.msoe.envsim.entities;


import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

public abstract class MobileEntity extends Entity {
    public MobileEntity(@NotNull Vector position, double radius, Color color, @NotNull Environment env) {
        super(position, radius, color, env);
    }

    public abstract void getDecision();
    public abstract void findCollisions();
}
