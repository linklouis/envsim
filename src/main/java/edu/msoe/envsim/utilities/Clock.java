package edu.msoe.envsim.utilities;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * A utility class for managing and regulating update rates in real-time systems.
 * The {@link Clock} tracks update intervals and enforces configurable minimum and maximum update rates.
 */
public class Clock {
    /**
     * A rolling total of the most recent update durations in nanoseconds,
     * used to calculate the average update rate efficiently.
     */
    private long runningTotal = 0;

    /**
     * The timestamp of the last update, in nanoseconds.
     */
    private long lastUpdateTime;

    /**
     * An array storing the most recent update durations, used to calculate the average update rate.
     */
    private final long[] updateTimes;

    /**
     * The current index in {@link #updateTimes} where the latest update duration will be stored.
     */
    private int updateTimeIndex = 0;

    /**
     * The calculated update rate (in updates per second), averaged over recent updates.
     * This value is volatile to ensure thread-safe visibility in concurrent environments.
     */
    private volatile double updateRate = 100;

    /**
     * A flag indicating whether the {@link Clock} is currently running.
     */
    private boolean running = false;

    /**
     * The minimum update rate (in updates per second) that the {@link Clock} will report.
     * If the calculated update rate is below this value, the {@link Clock} will nonetheless
     * report it to be {@code minRate}. This can help stabilize behavior during
     * periods of extremely slow updates or prevent anomalies.
     * <p>
     *     Values too high can result in very inaccurate update rates being reported.
     * </p>
     * <p>Default: {@code 0}</p>
     */
    private final double minRate;

    /**
     * The maximum number of updates allowed per second.
     * If the time elapsed between updates is less than {@code 1 / maxRate} seconds,
     * the {@link Clock} will enforce a delay to cap the update rate.
     * <p>Default: {@link Double#POSITIVE_INFINITY}</p>
     */
    private double maxRate;

    /**
     * The minimum number of nanoseconds any given frame is permitted to be.
     * Calculated as {@code 1_000_000_000.0 /} {@link #maxRate}.
     */
    private long minNanosPerUpdate;

    /**
     * The total nanoseconds represented by one full cycle of the {@link #updateTimes} array.
     * <p>
     * This value is precomputed as {@code 1_000_000_000.0 * updateTimes.length} to avoid recalculating
     * it on every frame update. It represents the theoretical total time for one full set of updates
     * at a rate of 1 update per second.
     * </p>
     */
    private final double nanoSecondsPerCycle;


    /**
     * Creates a new {@link Clock} instance for managing and regulating update rates.
     * <ul>
     *   <li>{@code memory}: 100 updates.</li>
     *   <li>{@code minRate}: 0 updates per second.</li>
     *   <li>{@code maxRate}: Unlimited updates (no cap).</li>
     * </ul>
     */
    public Clock() {
        this(100, 0, Double.POSITIVE_INFINITY);
    }

    /**
     * Creates a new {@link Clock} with a specified minimum update rate.
     * <ul>
     *   <li>{@code memory}: 100 updates.</li>
     *   <li>{@code maxRate}: Unlimited updates (no cap).</li>
     * </ul>
     *
     * @param minRate The minimum update rate (in updates per second) that the {@link Clock} will report.
     *                If the calculated rate is below this value, the {@link Clock} will report {@code minRate}.
     *                This can help stabilize behavior during slow updates.
     *
     * @throws IllegalArgumentException if {@code minRate < 0}.
     */
    public Clock(double minRate) {
        this(100, minRate, Double.POSITIVE_INFINITY);
    }

    /**
     * Creates a new {@link Clock} with specified minimum and maximum update rates.
     * <ul>
     *   <li>{@code memory}: 100 updates.</li>
     * </ul>
     *
     * @param minRate The minimum update rate (in updates per second) that the {@link Clock} will report.
     *                If the calculated rate is below this value, the {@link Clock} will report {@code minRate}.
     *                This can help stabilize behavior during slow updates.
     * @param maxRate The maximum number of updates allowed per second. If the time elapsed
     *                between updates is less than {@code 1 / maxRate} seconds, the {@link Clock}
     *                will enforce a delay to cap the update rate.
     *
     * @throws IllegalArgumentException if {@code minRate < 0}.
     * @throws IllegalArgumentException if {@code maxRate < 0}.
     * @throws IllegalArgumentException if {@code maxRate < minRate}.
     */
    public Clock(double minRate, double maxRate) {
        this(100, minRate, maxRate);
    }

    /**
     * Creates a new {@link Clock} with a specified memory size for averaging updates.
     * <ul>
     *   <li>{@code minRate}: 0 updates per second.</li>
     *   <li>{@code maxRate}: Unlimited updates (no cap).</li>
     * </ul>
     *
     * @param memory The number of recent updates to average when calculating the update rate.
     *               Larger values provide smoother averages but may react slower to changes.
     *
     * @throws IllegalArgumentException if {@code memory <= 1}.
     */
    public Clock(int memory) {
        this(memory, 0, Double.POSITIVE_INFINITY);
    }

    /**
     * Creates a new {@link Clock} with full configuration options.
     *
     * @param memory The number of recent updates to average when calculating the update rate.
     *               Larger values provide smoother averages but may react slower to changes.
     * @param minRate The minimum update rate (in updates per second) that the {@link Clock} will report.
     *                If the calculated rate is below this value, the {@link Clock} will report {@code minRate}.
     *                This can help stabilize behavior during slow updates.
     * @param maxRate The maximum number of updates allowed per second. If the time elapsed
     *                between updates is less than {@code 1 / maxRate} seconds, the {@link Clock}
     *                will enforce a delay to cap the update rate.
     *
     * @throws IllegalArgumentException if {@code memory <= 1}.
     * @throws IllegalArgumentException if {@code minRate < 0}.
     * @throws IllegalArgumentException if {@code maxRate < 0}.
     * @throws IllegalArgumentException if {@code maxRate < minRate}.
     */
    public Clock(int memory, double minRate, double maxRate) {
        if (memory < 1)         throw new IllegalArgumentException("Clock memory must be at least 1. Given: " + memory);
        if (minRate < 0)        throw new IllegalArgumentException("Clock's minimum update rate must be positive. Given: " + minRate);
        if (maxRate < 0)        throw new IllegalArgumentException("Clock's maximum update rate must be positive. Given: " + maxRate);
        if (maxRate < minRate)  throw new IllegalArgumentException("Clock's maximum update rate cannot be less than its minimum update rate." +
                "Given: maxRate=" + maxRate + ", minRate=" + minRate);

        updateTimes = new long[memory];
        this.minRate = minRate;
        this.maxRate = maxRate;
        this.minNanosPerUpdate = Math.round(1_000_000_000.0 / maxRate);
        this.nanoSecondsPerCycle = 1_000_000_000.0 * memory;
    }


    /*
     * Running the Clock:
     */

    /**
     * Starts the {@link Clock}, marking it as running and initializing the timing mechanism.
     * This method sets the {@link #lastUpdateTime} to the current time and prepares the {@link Clock} for updates.
     * <p>
     * If the {@link Clock} is already running, calling this method will reset the timing state without stopping.
     * </p>
     */
    public void start() {
        // TODO maybe make only work when not already running and add restart method for that case?
        running = true;
        lastUpdateTime = System.nanoTime();
    }

    /**
     * Updates the {@link Clock}'s state by calculating the elapsed time since the last update,
     * enforcing the minimum frame time, and updating the average update rate.
     * <p>
     * This method should be called on every iteration of the system's update loop.
     * </p>
     * <p>
     * It ensures that the update rate does not exceed the specified {@code maxRate}.
     * </p>
     */
    public void update() {
        enforceMinimumFrameTime();
        long elapsedNanos = System.nanoTime() - lastUpdateTime;

        calculateNewAverage(elapsedNanos);
        updateTimes[updateTimeIndex] = elapsedNanos;
        updateTimeIndex = (updateTimeIndex + 1) % updateTimes.length;

        lastUpdateTime = System.nanoTime();
    }


    /**
     * Enforces the minimum frame time as defined by {@link #minNanosPerUpdate}.
     * <p>
     * If the elapsed time since the last update is less than the required minimum frame time,
     * the method introduces a delay to ensure the frame duration meets the minimum threshold.
     * </p>
     * <p>
     * This helps regulate the maximum update rate to prevent excessively rapid updates.
     * </p>
     */
    @Contract(pure = true)
    private void enforceMinimumFrameTime() {
        long targetNanos = lastUpdateTime + minNanosPerUpdate - 100;
        while (System.nanoTime() < targetNanos) {
            Thread.yield(); // Yield control to other threads
        }
    }

    /**
     * Calculates the average update rate (in updates per second) based on the elapsed time between updates.
     * <p>
     * The method uses a circular buffer of recent update durations to calculate the average.
     * If the buffer is full, it computes the average based on all stored values.
     * </p>
     * <p>
     * This ensures smooth and stable update rate calculations over a configurable window of updates.
     * </p>
     *
     * @param elapsedNanos The elapsed time for the most recent update, in nanoseconds.
     */
    private void calculateNewAverage(long elapsedNanos) {
        runningTotal -= updateTimes[updateTimeIndex];
        runningTotal += elapsedNanos;
        updateTimes[updateTimeIndex] = elapsedNanos;
        updateRate = nanoSecondsPerCycle / runningTotal;
    }

    public void setMaxRate(double maxRate) {
        // TODO: Does this break anything?
        this.maxRate = maxRate;
        this.minNanosPerUpdate = (long) (1_000_000_000.0 / maxRate);
    }


    /*
     * Output:
     */

    /**
     * Checks whether the {@link Clock} is currently running.
     * <p>
     * A running {@link Clock} is actively tracking updates and enforcing frame time constraints.
     * </p>
     *
     * @return {@code true} if the {@link Clock} is running; {@code false} otherwise.
     */
    @Contract(pure = true)
    public boolean isRunning() {
        return running;
    }

    /**
     * @return The configured minimum update rate (in updates per second) for the {@link Clock}.
     */
    @Contract(pure = true)
    public double getMinRate() {
        return minRate;
    }

    /**
     * @return Retrieves the capped update rate (in updates per second),
     * ensuring it does not fall below the configured {@link #minRate}.
     */
    @Contract(pure = true)
    public synchronized double getUpdateRate() {
        return Math.max(updateRate, minRate);
    }


    /**
     * Retrieves the current calculated update rate (in updates per second).
     * <p>
     * This method does not apply any rate capping.
     * </p>
     *
     * @return The current update rate, in updates per second.
     */
    @Contract(pure = true)
    public synchronized double getRawUpdateRate() {
        return updateRate;
    }

    /**
     * Retrieves the current update rate formatted to a specified number of decimal places.
     *
     * @param decimalPlaces The number of decimal places to include in the result.
     * @return The formatted update rate.
     */
    @Contract(pure = true)
    public synchronized double getFormattedUpdateRate(int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces); // Calculate the scaling factor
        return Math.round(updateRate * scale) / scale; // Scale, round, and then scale back
    }

    /**
     * Returns a {@link Supplier} that provides the current update rate.
     * This can be useful for integrating the update rate into external systems
     * that require dynamic, on-demand access to the {@link Clock}'s state.
     *
     * @return A {@link Supplier} that supplies the current update rate.
     */
    public @NotNull Supplier<Double> getUpdateRateSupplier() {
        return this::getRawUpdateRate;
    }

    @Contract(pure = true)
    public double getMaxRate() {
        return maxRate;
    }

    @Contract(pure = true)
    public long getMinNanosPerUpdate() {
        return minNanosPerUpdate;
    }

    @Contract(pure = true)
    public int memory() {
        return updateTimes.length;
    }


    public static class ClockBuilder {
        private int memory = 100;
        private double minRate = 0;
        private double maxRate = Double.POSITIVE_INFINITY;

        public ClockBuilder memory(int memory) { this.memory = memory; return this; }
        public ClockBuilder minRate(double minRate) { this.minRate = minRate; return this; }
        public ClockBuilder maxRate(double maxRate) { this.maxRate = maxRate; return this; }
        public Clock build() { return new Clock(memory, minRate, maxRate); }
    }

}
