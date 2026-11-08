package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.utilities.MutableVector;
import edu.msoe.envsim.utilities.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class RayCaster {
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

        doOneStationaryRay(0, entity);
        doOneStationaryRay(1, entity);
        doOneStationaryRay(2, entity);
        doOneStationaryRay(3, entity);
        doOneStationaryRay(4, entity);
        doOneStationaryRay(5, entity);
        doOneStationaryRay(6, entity);
        doOneStationaryRay(7, entity);
        doOneStationaryRay(8, entity);
        doOneStationaryRay(9, entity);
        doOneStationaryRay(10, entity);
    }

    private void doOneStationaryRay(int index, @NotNull Entity entity) {
        double dist = getCircleCollisionDistance(origin, directions[index], entity.getPosition(), entity.getRadius());
        if (dist > 0 && dist <= closestIntersections[index]) {
            closestIntersections[index] = dist;
            closestEntities[index] = entity;
        }
    }

    public void visitMobile(@NotNull Entity entity, double centerDistance) {
        if (entity.getRadius() <= 0) return;

        if (doOneMobileRay(0, entity)) return;
        if (doOneMobileRay(1, entity)) return;
        if (doOneMobileRay(2, entity)) return;
        if (doOneMobileRay(3, entity)) return;
        if (doOneMobileRay(4, entity)) return;
        if (doOneMobileRay(5, entity)) return;
        if (doOneMobileRay(6, entity)) return;
        if (doOneMobileRay(7, entity)) return;
        if (doOneMobileRay(8, entity)) return;
        if (doOneMobileRay(9, entity)) return;
        doOneMobileRay(10, entity);
    }

    private boolean doOneMobileRay(int index, @NotNull Entity entity) {
        double dist = getCircleCollisionDistance(origin, directions[index], entity.getPosition(), entity.getRadius());
        if (dist > 0 && dist <= closestIntersections[index]) {
            // TODO: Not seeing members of your species could prevent more complex social behavior, but I'm not expecting that for now anyway. Might have to find a way around it at some point.
            if (speciesResolver.apply((Agent) entity)) return true; // Species resolution is slow. Only do it on Agents we know we care about.
            closestIntersections[index] = dist;
            closestEntities[index] = entity;
        }
        return false;
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
        resetIndex(0, direction);
        resetIndex(1, direction);
        resetIndex(2, direction);
        resetIndex(3, direction);
        resetIndex(4, direction);
        resetIndex(5, direction);
        resetIndex(6, direction);
        resetIndex(7, direction);
        resetIndex(8, direction);
        resetIndex(9, direction);
        resetIndex(10, direction);

        origin = position;
    }

    private void resetIndex(int index, double direction) {
        closestIntersections[index] = maxDistance;
        closestEntities[index] = null;
        directions[index].setAngle(direction + relativeDirections[index]);
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
