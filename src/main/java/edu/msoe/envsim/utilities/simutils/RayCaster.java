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
    private final Vector worldSize;
    private final double @NotNull [] relativeDirections;
    private final double maxDistance;
    private final double maxDistanceSq;
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
                     @NotNull Function<Agent, Boolean> speciesResolver, Vector worldSize) {
        if (maxDistance < 0) throw new IllegalArgumentException("maxDistance is negative");

        this.raySnapshots = new RaySnapshot[numRays];

        this.numRays = numRays;
        this.worldSize  = worldSize;
        this.relativeDirections = getDirections(fieldOfView, numRays);
        this.maxDistance = maxDistance;
        this.maxDistanceSq = maxDistance * maxDistance;
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

        double change = fieldOfView / (numRays - 1);
        double start = -fieldOfView / 2;
        for (int i = 0; i < numRays; i++) {
            relativeDirections[i] = start + change * i;
        }

        return relativeDirections;
    }

    public void visitStationary(@NotNull Entity entity, double centerDistanceSq) {
        double maxReach = maxDistance + entity.getRadius();
        if (entity.getRadius() <= 0 || centerDistanceSq > maxReach * maxReach) return;

        for (int i = 0; i < numRays; i++) {
            double dist = getCircleCollisionDistance(origin, directions[i], entity.getPosition(), entity.getRadius());
            if (dist > 0 && dist <= closestIntersections[i]) {
                closestIntersections[i] = dist;
                closestEntities[i] = entity;
            }
        }
    }

    public void visitMobile(@NotNull Entity entity, double centerDistanceSq) {
        double maxReach = maxDistance + entity.getRadius();
        if (entity.getRadius() <= 0 || centerDistanceSq > maxReach * maxReach) return;

        for (int i = 0; i < numRays; i++) {
            double dist = getCircleCollisionDistance(origin, directions[i], entity.getPosition(), entity.getRadius());
            if (dist > 0 && dist <= closestIntersections[i]) {
                // TODO: Not seeing members of your species could prevent more complex social behavior, but I'm not expecting that for now anyway. Might have to find a way around it at some point.
                if (speciesResolver.apply((Agent) entity))
                    return; // Species resolution is slow. Only do it on Agents we know we care about.
                closestIntersections[i] = dist;
                closestEntities[i] = entity;
            }
        }
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
        origin = position;

        // TODO: Why is this so expensive???
        for (int i = 0; i < numRays; i++) {
            closestEntities[i] = null;
            directions[i].setAngle(direction + relativeDirections[i]);

            double dx = directions[i].x();
            double dy = directions[i].y();

            double outDist = getOutDist(dx, dy);

            closestIntersections[i] = Math.min(outDist, maxDistance);
        }
    }

    /**
     * Source: Google Gemini, based on code from me
     * @param dx
     * @param dy
     * @return
     */
    private double getOutDist(double dx, double dy) {
        // Default to infinity if the ray is parallel to the walls
        double xOutDist = Double.POSITIVE_INFINITY;
        double yOutDist = Double.POSITIVE_INFINITY;

        if (dx != 0) {
            xOutDist = (dx > 0 ? worldSize.x() - origin.x() : -origin.x()) / dx;
        }

        if (dy != 0) {
            yOutDist = (dy > 0 ? worldSize.y() - origin.y() : -origin.y()) / dy;
        }

        // We only care about positive intersection distances (walls ahead of us)
        double outDist = Double.POSITIVE_INFINITY;
        if (xOutDist > 0) outDist = xOutDist;
        if (yOutDist > 0 && yOutDist < outDist) outDist = yOutDist;
        return outDist;
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
