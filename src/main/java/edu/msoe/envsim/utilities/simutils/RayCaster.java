package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RayCaster {
    public record RayData(double distance, @Nullable Entity entity, TerrainType terrain) {
    }

    /**
     * Ignores any object which the origin is inside.
     *  (So being close to another {@link Entity} doesn't totally blind the {@link Agent}.
     * @param origin The center of the {@link Agent} the ray originates from
     * @param direction The direction the ray is moving in. MUST BE A UNIT VECTOR
     * @param entities A {@link List} of {@link Entity}s in the area that the ray could potentially hit.
     * @return A {@link RayData} holding the information about the closest {@link Entity} the ray hit, or the location the ray ended if it doesn't hit an entity.
     * Returns {@code null} if the ray is length 0 or less.
     */
    public static @Nullable RayData castRay(@NotNull Vector origin, @NotNull Vector direction, double maxDistance, @NotNull List<Entity> entities) {
        double closestIntersection = maxDistance;
        if (closestIntersection <= 0) return null;

        Entity closestEntity = null;

        for (Entity entity : entities) {
            double distance = getCircleCollisionDistance(origin, direction, entity.getPosition(), entity.getRadius());
            if (distance > 0 && distance <= closestIntersection) {
                closestIntersection = distance;
                closestEntity = entity;
            }
        }

        // TODO: Add the terrain type at the location the ray ends if it doesn't hit anything.
        TerrainType terrainType = closestEntity == null ? null : closestEntity.getTerrainType();
        return new RayData(closestIntersection, closestEntity, terrainType);
    }

    /**
     *
     * @param origin
     * @param direction MUST BE A UNIT VECTOR
     * @param center
     * @param radius
     * @return
     */
    private static double getCircleCollisionDistance(@NotNull Vector origin, @NotNull Vector direction, @NotNull Vector center, double radius) {
        Vector toCenter = center.subtract(origin);

        /* TODO: We can use the distance to the closest point as the distance to the Entity.
         *  This would mean it's more based on the center of the entity rather than the outside edge.
         *  This could mean we don't see larger agents as soon as we should, but it avoids the sqrt.
         */
        double distToClosestPt = direction.dot(toCenter);

        // TODO: Manually do these calculations for speed
        Vector closestPointOnRay = origin.add(direction.scale(distToClosestPt));
        Vector dist = center.subtract(closestPointOnRay);
        double distSq = dist.magnitudeSquared();

        double radiusSq = radius * radius;
        if (distSq > radiusSq) return -1;
        return distToClosestPt - Math.sqrt(radiusSq - distSq);
    }
}
