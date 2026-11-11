package edu.msoe.envsim.entities;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.utilities.Vector;
import edu.msoe.envsim.utilities.simutils.TerrainType;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public abstract class StationaryEntity extends Entity {
    private TerrainType terrainType;
    private final Function<Vector, TerrainType> terrainTypeSupplier;

    public StationaryEntity(Vector position, double radius, Color color, @NotNull Environment env) {
        super(position, radius, color, env);
        terrainType = env.getTerrainType(getPosition());
        terrainTypeSupplier = env::getTerrainType;
    }

    public void updateWorldState() {
        setTerrainType(terrainTypeSupplier.apply(getPosition()));
    }

    @Override
    public TerrainType getTerrainType() {
        return terrainType;
    }

    protected void setTerrainType(TerrainType terrainType) {
        this.terrainType = terrainType;
    }
}
