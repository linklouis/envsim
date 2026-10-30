package edu.msoe.envsim.utilities.simutils;

import edu.msoe.envsim.entities.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A uniform spatial grid that is <b>rebuilt in bulk</b> instead of being updated entity by entity.
 * <p>
 * Entities are counting-sorted by cell into flat arrays, in row-major cell order
 * ({@code cell = cy * cellsPerAxis + cx}). That layout has two useful properties:
 * </p>
 * <ul>
 *     <li>All entities of one cell are adjacent in memory, and so are all entities of several
 *         horizontally adjacent cells, so scanning one row of cells is a single linear loop.</li>
 *     <li>Positions are copied into primitive {@code double[]} arrays, so range checks never touch
 *         the {@link Entity} objects. An entity is only dereferenced once it is actually in range.</li>
 * </ul>
 * <p>
 * <b>Threading:</b> {@link #rebuild(Collection)} must be called from one thread while no other thread is
 * querying. Queries ({@link #forEachInRange}, {@link #collectCollisions}) only read, so any number of threads
 * may run them concurrently, as long as a barrier (such as the end of a parallel stream) separates them from
 * the last rebuild.
 * </p>
 * <p>
 * Cell mapping matches the old incremental grid: {@code index = clamp((int) (coordinate / cellSize), 0, cellsPerAxis - 1)}.
 * </p>
 */
public final class SpatialGrid {

    /** Receives each entity found by {@link #forEachInRange}. Primitive {@code double} avoids boxing. */
    @FunctionalInterface
    public interface Visitor {
        void visit(Entity entity, double distance);
    }

    private final int cellsPerAxis;
    private final int cellCount;
    private final double invCellWidth;
    private final double invCellHeight;
    private final boolean monotonicRadiusBound;

    /** {@code starts[c] .. starts[c + 1]} is the slice of the sorted arrays that belongs to cell {@code c}. */
    private final int[] starts;
    /** Scratch copy of {@link #starts} used as write cursors while scattering. */
    private final int[] cursor;

    private Entity[] entities = new Entity[0];
    private double[] xs = new double[0];
    private double[] ys = new double[0];

    // Scratch, only used inside rebuild():
    private Entity[] scratch = new Entity[0];
    private int[] cellOf = new int[0];

    private int count = 0;
    private double radiusBound = 0;

    /**
     * @param cellsPerAxis         Number of cells along each axis.
     * @param cellWidth            Width of one cell in world units.
     * @param cellHeight           Height of one cell in world units.
     * @param monotonicRadiusBound If {@code true}, the radius bound used to pre-filter collision candidates
     *                             never decreases between rebuilds. Use this for entities whose radius changes
     *                             but never exceeds the radius they were registered with (for example Food, which
     *                             shrinks when eaten and regrows to its starting size). For entities whose radius
     *                             is stable between a rebuild and the queries that follow it (agents),
     *                             pass {@code false} to keep the bound tight.
     */
    public SpatialGrid(int cellsPerAxis, double cellWidth, double cellHeight, boolean monotonicRadiusBound) {
        this.cellsPerAxis = cellsPerAxis;
        this.cellCount = cellsPerAxis * cellsPerAxis;
        this.invCellWidth = 1.0 / cellWidth;
        this.invCellHeight = 1.0 / cellHeight;
        this.monotonicRadiusBound = monotonicRadiusBound;
        this.starts = new int[cellCount + 1];
        this.cursor = new int[cellCount];
    }

    /**
     * Replaces the grid's contents with {@code source}, using each entity's position and radius as they are right now.
     * Allocation-free once the internal arrays have grown to the population size.
     */
    public void rebuild(@NotNull Collection<? extends Entity> source) {
        ensureScratchCapacity(source.size());
        Arrays.fill(starts, 0);

        int n = 0;
        double maxRadius = 0;
        for (Entity e : source) {
            if (n == scratch.length) ensureScratchCapacity(n + 1);   // source grew while iterating
            int cell = cellY(e.y()) * cellsPerAxis + cellX(e.x());
            scratch[n] = e;
            cellOf[n] = cell;
            starts[cell + 1]++;
            double r = e.getRadius();
            if (r > maxRadius) maxRadius = r;
            n++;
        }

        // Prefix sums: starts[c] becomes the first index of cell c.
        for (int c = 0; c < cellCount; c++) {
            starts[c + 1] += starts[c];
        }
        System.arraycopy(starts, 0, cursor, 0, cellCount);

        ensureSortedCapacity(n);
        for (int i = 0; i < n; i++) {
            Entity e = scratch[i];
            int p = cursor[cellOf[i]]++;
            entities[p] = e;
            xs[p] = e.x();
            ys[p] = e.y();
            scratch[i] = null;
        }
        if (count > n) {
            Arrays.fill(entities, n, count, null);   // don't keep removed entities alive
        }
        count = n;
        radiusBound = monotonicRadiusBound ? Math.max(radiusBound, maxRadius) : maxRadius;
    }

    /**
     * Calls {@code visitor} for every entity (other than {@code self}) within {@code maxDistance}
     * of the point {@code (px, py)}.
     *
     * @param maxDistSq {@code maxDistance * maxDistance}, passed in so callers can cache it.
     */
    public void forEachInRange(Entity self, double px, double py, double maxDistance, double maxDistSq, Visitor visitor) {
        if (count == 0) return;

        int minCX = cellX(px - maxDistance);
        int maxCX = cellX(px + maxDistance);
        int minCY = cellY(py - maxDistance);
        int maxCY = cellY(py + maxDistance);

        for (int cy = minCY; cy <= maxCY; cy++) {
            int rowBase = cy * cellsPerAxis;
            int end = starts[rowBase + maxCX + 1];
            for (int i = starts[rowBase + minCX]; i < end; i++) {
                double dx = xs[i] - px;
                double dy = ys[i] - py;
                double distSq = dx * dx + dy * dy;
                if (distSq <= maxDistSq) {
                    Entity other = entities[i];
                    if (other != self) {
                        visitor.visit(other, distSq);
                    }
                }
            }
        }
    }

    /**
     * Adds to {@code out} every entity (other than {@code self}) that {@code self} is colliding with
     * and that satisfies {@code requirement}.
     * <p>
     * Candidates are first filtered using only the primitive position arrays and a conservative radius bound,
     * and the exact {@link Entity#isColliding(Entity)} test runs only on the survivors.
     * </p>
     */
    public void collectCollisions(Entity self, BiPredicate<Entity, Entity> collisionRequirement, List<Entity> out) {
        if (count == 0) return;

        double px = self.x();
        double py = self.y();
        double reach = self.getRadius() + radiusBound;
        double reachSq = reach * reach;

        int minCX = cellX(px - reach);
        int maxCX = cellX(px + reach);
        int minCY = cellY(py - reach);
        int maxCY = cellY(py + reach);

        for (int cy = minCY; cy <= maxCY; cy++) {
            int rowBase = cy * cellsPerAxis;
            int end = starts[rowBase + maxCX + 1];
            for (int i = starts[rowBase + minCX]; i < end; i++) {
                double dx = xs[i] - px;
                double dy = ys[i] - py;
                if (dx * dx + dy * dy <= reachSq) {
                    Entity other = entities[i];
                    if (other != self && self.isColliding(other) && collisionRequirement.test(self, other)) {
                        out.add(other);
                    }
                }
            }
        }
    }

    public int size() {
        return count;
    }

    private int cellX(double x) {
        double v = x * invCellWidth;
        return v <= 0 ? 0 : (v >= cellsPerAxis - 1 ? cellsPerAxis - 1 : (int) v);
    }

    private int cellY(double y) {
        double v = y * invCellHeight;
        return v <= 0 ? 0 : (v >= cellsPerAxis - 1 ? cellsPerAxis - 1 : (int) v);
    }

    private void ensureScratchCapacity(int minCapacity) {
        if (scratch.length < minCapacity) {
            int capacity = Math.max(minCapacity, scratch.length * 2);
            scratch = Arrays.copyOf(scratch, capacity);
            cellOf = Arrays.copyOf(cellOf, capacity);
        }
    }

    private void ensureSortedCapacity(int minCapacity) {
        if (entities.length < minCapacity) {
            int capacity = Math.max(minCapacity, entities.length * 2);
            entities = Arrays.copyOf(entities, capacity);
            xs = Arrays.copyOf(xs, capacity);
            ys = Arrays.copyOf(ys, capacity);
        }
    }
}
