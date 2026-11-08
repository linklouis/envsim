package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.utilities.MutableVector;
import edu.msoe.envsim.utilities.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class RayCaster {
    // TODO: Update this to hold arrays for each and only visit each entity once.
    public record RayData(double distance, @Nullable Entity entity, TerrainType terrain) {
    }

    public record RaySnapshot(Vector direction, double distance, @Nullable Entity entity, TerrainType terrain) {}
    private final RaySnapshot[] raySnapshots;

    private final int numRays;
    private final double @NotNull [] relativeDirections;
    private final double maxDistance;
    private final @NotNull Function<Vector, TerrainType> terrainTypeResolver;
    private final @NotNull Function<Agent, Boolean> speciesResolver;
    private @NotNull Vector origin;
    private final @NotNull MutableVector @NotNull [] directions;
    private final double @NotNull [] closestIntersections;
    private final @Nullable Entity @NotNull [] closestEntities;

    private final SpatialGrid.Visitor stationaryVisitor;
    private final SpatialGrid.Visitor mobileVisitor;

    public RayCaster(@NotNull Vector origin, double fieldOfView, int numRays, double maxDistance, double direction,
                     @NotNull Function<Vector, TerrainType> terrainTypeResolver,
                     @NotNull Function<Agent, Boolean> speciesResolver) {
        if (maxDistance < 0) throw new IllegalArgumentException("maxDistance is negative");

        this.raySnapshots = new RaySnapshot[numRays];

        this.numRays = numRays;
        this.relativeDirections = getDirections(fieldOfView, numRays);
        this.maxDistance = maxDistance;
        this.terrainTypeResolver = terrainTypeResolver;
        this.speciesResolver = speciesResolver;
        this.closestIntersections = new double[numRays];
        this.closestEntities = new Entity[numRays];
        this.directions = new MutableVector[numRays];

        for  (int i = 0; i < numRays; i++) {
            closestIntersections[i] = maxDistance;
            closestEntities[i] = null;
            directions[i] = MutableVector.fromAngle(direction + relativeDirections[i]);
        }
        this.origin = origin;

        stationaryVisitor = this::visitStationary;
        mobileVisitor = this::visitMobile;
    }

    public static double @NotNull [] getDirections(double fieldOfView, int numRays) {
        if (numRays <= 0) return new double[0];
        if (numRays % 2 == 0) throw new  IllegalArgumentException("numRays must be odd");
        double[] relativeDirections = new double[numRays];

        double change = fieldOfView / numRays;
        double start = -fieldOfView / 2;
        for (int i = 0; i < numRays; i++) {
            relativeDirections[i] = start + change * i;
        }

        return relativeDirections;
    }

    public void visitStationary(@NotNull Entity entity, double centerDistance) {
        if (entity.getRadius() <= 0) return;

        // Explicitly unrolled for numRays = 11
        double d0 = getCircleCollisionDistance(origin, directions[0], entity.getPosition(), entity.getRadius());
        if (d0 > 0 && d0 <= closestIntersections[0]) { closestIntersections[0] = d0; closestEntities[0] = entity; }

        double d1 = getCircleCollisionDistance(origin, directions[1], entity.getPosition(), entity.getRadius());
        if (d1 > 0 && d1 <= closestIntersections[1]) { closestIntersections[1] = d1; closestEntities[1] = entity; }

        double d2 = getCircleCollisionDistance(origin, directions[2], entity.getPosition(), entity.getRadius());
        if (d2 > 0 && d2 <= closestIntersections[2]) { closestIntersections[2] = d2; closestEntities[2] = entity; }

        double d3 = getCircleCollisionDistance(origin, directions[3], entity.getPosition(), entity.getRadius());
        if (d3 > 0 && d3 <= closestIntersections[3]) { closestIntersections[3] = d3; closestEntities[3] = entity; }

        double d4 = getCircleCollisionDistance(origin, directions[4], entity.getPosition(), entity.getRadius());
        if (d4 > 0 && d4 <= closestIntersections[4]) { closestIntersections[4] = d4; closestEntities[4] = entity; }

        double d5 = getCircleCollisionDistance(origin, directions[5], entity.getPosition(), entity.getRadius());
        if (d5 > 0 && d5 <= closestIntersections[5]) { closestIntersections[5] = d5; closestEntities[5] = entity; }

        double d6 = getCircleCollisionDistance(origin, directions[6], entity.getPosition(), entity.getRadius());
        if (d6 > 0 && d6 <= closestIntersections[6]) { closestIntersections[6] = d6; closestEntities[6] = entity; }

        double d7 = getCircleCollisionDistance(origin, directions[7], entity.getPosition(), entity.getRadius());
        if (d7 > 0 && d7 <= closestIntersections[7]) { closestIntersections[7] = d7; closestEntities[7] = entity; }

        double d8 = getCircleCollisionDistance(origin, directions[8], entity.getPosition(), entity.getRadius());
        if (d8 > 0 && d8 <= closestIntersections[8]) { closestIntersections[8] = d8; closestEntities[8] = entity; }

        double d9 = getCircleCollisionDistance(origin, directions[9], entity.getPosition(), entity.getRadius());
        if (d9 > 0 && d9 <= closestIntersections[9]) { closestIntersections[9] = d9; closestEntities[9] = entity; }

        double d10 = getCircleCollisionDistance(origin, directions[10], entity.getPosition(), entity.getRadius());
        if (d10 > 0 && d10 <= closestIntersections[10]) { closestIntersections[10] = d10; closestEntities[10] = entity; }
    }

    public void visitMobile(@NotNull Entity entity, double centerDistance) {
        if (entity.getRadius() <= 0) return;

        // TODO: Not seeing members of your species could prevent more complex social behavior, but I'm not expecting that for now anyway. Might have to find a way around it at some point.
        if (entity instanceof Agent agent && speciesResolver.apply(agent)) {
            return;
        }

        // Explicitly unrolled for numRays = 11
        double d0 = getCircleCollisionDistance(origin, directions[0], entity.getPosition(), entity.getRadius());
        if (d0 > 0 && d0 <= closestIntersections[0]) { closestIntersections[0] = d0; closestEntities[0] = entity; }

        double d1 = getCircleCollisionDistance(origin, directions[1], entity.getPosition(), entity.getRadius());
        if (d1 > 0 && d1 <= closestIntersections[1]) { closestIntersections[1] = d1; closestEntities[1] = entity; }

        double d2 = getCircleCollisionDistance(origin, directions[2], entity.getPosition(), entity.getRadius());
        if (d2 > 0 && d2 <= closestIntersections[2]) { closestIntersections[2] = d2; closestEntities[2] = entity; }

        double d3 = getCircleCollisionDistance(origin, directions[3], entity.getPosition(), entity.getRadius());
        if (d3 > 0 && d3 <= closestIntersections[3]) { closestIntersections[3] = d3; closestEntities[3] = entity; }

        double d4 = getCircleCollisionDistance(origin, directions[4], entity.getPosition(), entity.getRadius());
        if (d4 > 0 && d4 <= closestIntersections[4]) { closestIntersections[4] = d4; closestEntities[4] = entity; }

        double d5 = getCircleCollisionDistance(origin, directions[5], entity.getPosition(), entity.getRadius());
        if (d5 > 0 && d5 <= closestIntersections[5]) { closestIntersections[5] = d5; closestEntities[5] = entity; }

        double d6 = getCircleCollisionDistance(origin, directions[6], entity.getPosition(), entity.getRadius());
        if (d6 > 0 && d6 <= closestIntersections[6]) { closestIntersections[6] = d6; closestEntities[6] = entity; }

        double d7 = getCircleCollisionDistance(origin, directions[7], entity.getPosition(), entity.getRadius());
        if (d7 > 0 && d7 <= closestIntersections[7]) { closestIntersections[7] = d7; closestEntities[7] = entity; }

        double d8 = getCircleCollisionDistance(origin, directions[8], entity.getPosition(), entity.getRadius());
        if (d8 > 0 && d8 <= closestIntersections[8]) { closestIntersections[8] = d8; closestEntities[8] = entity; }

        double d9 = getCircleCollisionDistance(origin, directions[9], entity.getPosition(), entity.getRadius());
        if (d9 > 0 && d9 <= closestIntersections[9]) { closestIntersections[9] = d9; closestEntities[9] = entity; }

        double d10 = getCircleCollisionDistance(origin, directions[10], entity.getPosition(), entity.getRadius());
        if (d10 > 0 && d10 <= closestIntersections[10]) { closestIntersections[10] = d10; closestEntities[10] = entity; }
    }

    public RayData[] getRayData() {
        RayData[] data = new RayData[numRays];
        for (int i = 0; i < numRays; i++) {
            Entity closestEntity = closestEntities[i];
            TerrainType terrainType = closestEntity == null
                    ? terrainTypeResolver.apply(origin.addScaled(directions[i], maxDistance))
                    : closestEntity.getTerrainType();
            data[i] = new RayData(closestIntersections[i], closestEntity, terrainType);

            // Capture a thread-safe snapshot for the rendering thread
            raySnapshots[i] = new RaySnapshot(directions[i].getImmutable(), closestIntersections[i], closestEntity, terrainType);
        }
        return data;
    }

    public void reset(Vector position, double direction) {
        for  (int i = 0; i < numRays; i++) {
            closestIntersections[i] = maxDistance;
            closestEntities[i] = null;
            directions[i].setAngle(direction + relativeDirections[i]);
        }
        origin = position;
    }

    /**
     * TODO: ALSO CONSIDER THE WORLD BOUNDS
     * @param origin
     * @param direction MUST BE A UNIT VECTOR
     * @param center
     * @param radius
     * @return
     */
    private double getCircleCollisionDistance(@NotNull Vector origin, @NotNull MutableVector direction, @NotNull Vector center, double radius) {
        /* TODO: We can use the distance to the closest point as the distance to the Entity.
         *  This would mean it's more based on the center of the entity rather than the outside edge.
         *  This could mean we don't see larger agents as soon as we should, but it avoids the sqrt.
         */
        double distToClosestPt = direction.dotSubtracted(center, origin);

        double distX = center.x() - (origin.x() + direction.x() * distToClosestPt);
        double distY = center.y() - (origin.y() + direction.y() * distToClosestPt);
        double distSq = distX * distX + distY * distY;

        double radiusSq = radius * radius;
        if (distSq > radiusSq) return -1;
        return distToClosestPt - Math.sqrt(radiusSq - distSq);
    }

    public SpatialGrid.Visitor getStationaryVisitor() {
        return stationaryVisitor;
    }

    public SpatialGrid.Visitor getMobileVisitor() {
        return mobileVisitor;
    }

    public RaySnapshot[] getRaySnapshots() {
        return raySnapshots;
    }
}
