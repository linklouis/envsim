package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.Environment;
import edu.msoe.envsim.entities.Entity;
import edu.msoe.envsim.entities.Agent;
import edu.msoe.envsim.utilities.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;

public class RayCaster {
    public record RayData(double distance, @Nullable Entity entity, TerrainType terrain) {
    }

    /**
     * Ignores any object which the origin is inside.
     *  (So being close to another {@link Entity} doesn't totally blind the {@link Agent}.
     * @param origin The center of the {@link Agent} the ray originates from
     * @param direction The direction the ray is moving in. MUST BE A UNIT VECTOR
     * @param entities A {@link Collection} of {@link Entity}s in the area that the ray could potentially hit.
     * @return A {@link RayData} holding the information about the closest {@link Entity} the ray hit, or the location the ray ended if it doesn't hit an entity.
     * Returns {@code null} if the ray is length 0 or less.
     */
    public static @Nullable RayData castRay(@NotNull Vector origin, @NotNull Vector direction, double maxDistance,
                                            @NotNull Collection<Entity> entities,
                                            @NotNull Function<Vector, TerrainType> terrainTypeResolver) {
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

        TerrainType terrainType = closestEntity == null
                ? terrainTypeResolver.apply(origin.add(direction.scale(maxDistance)))
                : closestEntity.getTerrainType();
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

    @Contract("_, _ -> new")
    public static double @NotNull [] calculateRayDirections(double fieldOfView, int numRays) {
        if (numRays <= 0) return new double[0];
        if (numRays % 2 == 0) throw new  IllegalArgumentException("numRays must be odd");
        double[] rayDirections = new double[numRays];

        double change = fieldOfView / numRays;
        double start = - fieldOfView / 2;
        for (int i = 0; i < numRays; i++) {
            rayDirections[i] = start + change * i;
        }

        return rayDirections;
    }
}
