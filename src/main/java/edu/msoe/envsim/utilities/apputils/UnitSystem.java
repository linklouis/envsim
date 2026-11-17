package edu.msoe.envsim.utilities.apputils;

import edu.msoe.envsim.utilities.simutils.PhysicsMath;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The unit systems the UI can show physical quantities in.
 * <p>
 * The simulation itself works in pixels and ticks:
 * lengths are in pixels ({@link PhysicsMath#PIXELS_PER_METER} pixels make a meter), speeds are in pixels per tick,
 * accelerations are in pixels per tick squared, and masses already come out in kilograms.
 * </p>
 * <ul>
 *     <li>{@link #PIXELS} shows those raw simulation values.</li>
 *     <li>{@link #METRIC} and {@link #IMPERIAL} convert pixels to real-world lengths, and ticks to seconds
 *         using {@link #TICKS_PER_SECOND}.</li>
 * </ul>
 */
public enum UnitSystem {
    PIXELS("Pixels"),
    METRIC("Metric"),
    IMPERIAL("Imperial");

    /**
     * How many simulation ticks make one second of simulated time. This is the same 60 ticks per second
     * as the real-time speed preset on the simulation speed slider, so "real-time" really is real-time.
     * Change it here if you decide a tick should be a different length of time.
     */
    public static final double TICKS_PER_SECOND = 60.0;

    private static final double FEET_PER_METER = 3.280839895;
    private static final double POUNDS_PER_KILOGRAM = 2.20462262;

    private final String displayName;

    UnitSystem(@NotNull String displayName) {
        this.displayName = displayName;
    }

    public @NotNull String getDisplayName() {
        return displayName;
    }

    /**
     * @return The unit system after this one, wrapping around. Used by the buttons that cycle through them.
     */
    public @NotNull UnitSystem next() {
        UnitSystem[] allSystems = values();
        return allSystems[(ordinal() + 1) % allSystems.length];
    }


    /*
     * Length (the simulation's lengths are in pixels):
     */

    public @NotNull String lengthValue(double pixels) {
        return formatNumber(convertLength(pixels));
    }

    public @NotNull String lengthUnit() {
        return switch (this) {
            case PIXELS -> "px";
            case METRIC -> "m";
            case IMPERIAL -> "ft";
        };
    }

    public @NotNull String formatLength(double pixels) {
        return lengthValue(pixels) + " " + lengthUnit();
    }

    private double convertLength(double pixels) {
        return switch (this) {
            case PIXELS -> pixels;
            case METRIC -> pixels / PhysicsMath.PIXELS_PER_METER;
            case IMPERIAL -> pixels / PhysicsMath.PIXELS_PER_METER * FEET_PER_METER;
        };
    }


    /*
     * Speed and acceleration (pixels per tick, and pixels per tick squared):
     */

    public @NotNull String formatSpeed(double pixelsPerTick) {
        double value = (this == PIXELS) ? pixelsPerTick : convertLength(pixelsPerTick) * TICKS_PER_SECOND;
        String unit = switch (this) {
            case PIXELS -> "px/tick";
            case METRIC -> "m/s";
            case IMPERIAL -> "ft/s";
        };
        return formatNumber(value) + " " + unit;
    }

    public @NotNull String formatAcceleration(double pixelsPerTickSquared) {
        double value = (this == PIXELS)
                ? pixelsPerTickSquared
                : convertLength(pixelsPerTickSquared) * TICKS_PER_SECOND * TICKS_PER_SECOND;
        String unit = switch (this) {
            case PIXELS -> "px/tick²";
            case METRIC -> "m/s²";
            case IMPERIAL -> "ft/s²";
        };
        return formatNumber(value) + " " + unit;
    }


    /*
     * Mass (the simulation's masses are already in kilograms):
     */

    public @NotNull String formatMass(double kilograms) {
        return switch (this) {
            case PIXELS, METRIC -> formatNumber(kilograms) + " kg";
            case IMPERIAL -> formatNumber(kilograms * POUNDS_PER_KILOGRAM) + " lb";
        };
    }


    /*
     * Angles (degrees everywhere; only the time unit of a turn rate changes):
     */

    public @NotNull String formatTurnRate(double degreesPerTick) {
        if (this == PIXELS) {
            return formatNumber(degreesPerTick) + " °/tick";
        }
        return formatNumber(degreesPerTick * TICKS_PER_SECOND) + " °/s";
    }

    public static @NotNull String formatAngle(double radians) {
        return formatNumber(Math.toDegrees(radians)) + "°";
    }


    /*
     * Time (the simulation's unit of time is the tick):
     */

    /**
     * @param ticks A length of simulated time. Shown as it is for pixels, and as clock time for the real-world systems.
     */
    public @NotNull String formatDuration(double ticks) {
        if (this == PIXELS) {
            return formatWholeNumber(Math.round(ticks)) + " ticks";
        }

        long totalSeconds = Math.round(ticks / TICKS_PER_SECOND);
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }


    /*
     * Plain numbers:
     */

    /**
     * Formats with thousands separators, and with extra decimals for small values so they don't all show as 0.00.
     */
    public static @NotNull String formatNumber(double value) {
        double magnitude = Math.abs(value);
        int decimals;
        if (magnitude == 0 || magnitude >= 1) {
            decimals = 2;
        } else if (magnitude >= 0.01) {
            decimals = 3;
        } else {
            decimals = 4;
        }
        return String.format(Locale.US, "%,." + decimals + "f", value);
    }

    public static @NotNull String formatWholeNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}