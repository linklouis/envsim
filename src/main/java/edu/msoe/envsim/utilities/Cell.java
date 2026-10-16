package edu.msoe.envsim.utilities;

import edu.msoe.envsim.entities.Entity;

import java.util.concurrent.CopyOnWriteArraySet;

public class Cell extends CopyOnWriteArraySet<Entity> {
    private TerrainType terrainType;


    public Cell() {
        this(TerrainType.STANDARD);
    }

    public Cell(TerrainType terrainType) {
        super();
        this.terrainType = terrainType;
    }


    public TerrainType getTerrainType() {
        return terrainType;
    }

    public void setTerrainType(TerrainType terrainType) {
        this.terrainType = terrainType;
    }
}
