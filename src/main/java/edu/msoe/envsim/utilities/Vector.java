package edu.msoe.envsim.utilities;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles 2D vector math to simplify describing 2D objects.
 * <p>
 *     Keep in mind that Vectors are immutable.
 *     Calling {@code myVector.add(myOtherVector);} will do nothing.
 *     To update a vector, instead do: {@code myVector = myVector.add(myOtherVector);}
 *     This allows multiple vector operations
 *     to be performed cleanly: {@code someValue = a.add(b).subtract(c).dot(e)}
 * </p>
 * <P>
 *     This class is a record, which just means it stores some final values and we don't need to write constructors, getters, or setters.
 *     You can access the coordinates with .x() and .y()
 * </P>
 *
 * @param x The X component of the vector
 * @param y The Y component of the vector
 */
//@SuppressWarnings("unused")
public record Vector(double x, double y) {
    public static final @NotNull Vector ZERO = new Vector(0, 0);
    public static final @NotNull Vector I = new Vector(1, 0);
    public static final @NotNull Vector J = new Vector(0, 1);
    public static final @NotNull Vector ONE = new Vector(1, 1);

    /**
     * @return A new unit {@link Vector} in a random direction.
     */
    public static @NotNull Vector random() {
        return fromAngle(ThreadLocalRandom.current().nextDouble() * 2 * Math.PI);
    }

    public static @NotNull Vector random(double scale) {
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        return new Vector(Math.cos(angle) * scale, Math.sin(angle) * scale);
    }

    /**
     * @param min The minimum possible magnitude
     * @param max The maximum possible magnitude
     * @return A new {@link Vector} with a magnitude between {@code min} and {@code max}.
     */
    public static @NotNull Vector random(double min, double max) {
        return random().scale(2).subtract(ONE).atMagnitude(min + ThreadLocalRandom.current().nextDouble()*(max - min));
    }

    @Contract("_, _ -> new")
    public static @NotNull Vector random(@NotNull Vector min, @NotNull Vector max) {
        return new Vector(min.x + ThreadLocalRandom.current().nextDouble()*(max.x - min.x),
                min.y + ThreadLocalRandom.current().nextDouble()*(max.y - min.y));
    }

    @Contract("_ -> new")
    public static @NotNull Vector fromAngle(double angle) {
        return  new Vector(Math.cos(angle), Math.sin(angle));
    }


    @Contract(pure = true)
    public @NotNull Vector add(@NotNull Vector other) {
        return new Vector(x() + other.x(), y() + other.y());
    }

    @Contract(pure = true)
    public @NotNull Vector add(double otherX, double otherY) {
        return new Vector(x() + otherX, y() + otherY);
    }

    public @NotNull Vector addScaledInBounds(@NotNull Vector other, @NotNull Vector min, @NotNull Vector max, double scale) {
        double x = x() + other.x() * scale;
        double y = y() + other.y() * scale;
        return new Vector(
                Math.clamp(x, min.x(), max.x()),
                Math.clamp(y, min.y(), max.y())
        );
    }

    @Contract(pure = true)
    public @NotNull Vector subtract(@NotNull Vector other) {
        return new Vector(x() - other.x(), y() - other.y());
    }

    @Contract(pure = true)
    public @NotNull Vector subtract(double otherX, double otherY) {
        return new Vector(x() - otherX, y() - otherY);
    }

    @Contract(pure = true)
    public double dot(@NotNull Vector other) {
        return x() * other.x() + y() * other.y();
    }

    @Contract(pure = true)
    public @NotNull Vector scale(double factor) {
        return new Vector(x() * factor, y() * factor);
    }

    @Contract(pure = true)
    public @NotNull Vector normalize() {
        return scale(1/magnitude());
    }

    @Contract(pure = true)
    public @NotNull Vector atMagnitude(double newMagnitude) {
        return scale(newMagnitude/magnitude());
    }

    @Contract(pure = true)
    public double distanceSquared(@NotNull Vector o) {
        double dx = x - o.x, dy = y - o.y;
        return dx*dx + dy*dy;
    }
    public double distance(Vector o) {
        return Math.sqrt(distanceSquared(o));
    }


    @Contract(pure = true)
    public double magnitude() {
        return Math.hypot(x(), y());
    }

    @Contract(" -> new")
    public @NotNull Vector signs() {
        return new Vector(
                x() < 0 ? -1 : 1,
                y() < 0 ? -1 : 1
        );
    }

    @Contract("_, _ -> new")
    public @NotNull Vector blendWith(@NotNull Vector other, double interpolationStrength) {
        interpolationStrength = Math.clamp(interpolationStrength, 0, 1);
        double complement = 1.0 - interpolationStrength;
        return new Vector(
                x + (other.x - x) * interpolationStrength,
                y + (other.y - y) * interpolationStrength
        );
    }

    @Contract(" -> new")
    public @NotNull Vector abs() {
        return new Vector(
                Math.abs(x()),
                Math.abs(y())
        );
    }

    @Contract("_, _ -> new")
    public @NotNull Vector clamp(@NotNull Vector min, @NotNull Vector max) {
        return new Vector(
                Math.clamp(x(), min.x(), max.x()),
                Math.clamp(y(), min.y(), max.y())
        );
    }


    public double angle() {
        return Math.atan2(y(), x());
    }

    @Contract("_ -> new")
    public @NotNull Vector turn(double degrees) {
        return fromAngle(angle() + Math.toRadians(degrees));
    }

    @Contract("_ -> new")
    public @NotNull Vector elementWiseDivision(@NotNull Vector other) {
        return new Vector(x() / other.x(), y() / other.y());
    }

    public @NotNull Vector clampedElementWiseDivision(@NotNull Vector other, double min, double max) {
        return new Vector(
                (int) Math.clamp(x() / other.x(), min, max),
                (int) Math.clamp(y() / other.y(), min, max)
        );
    }

    public boolean equals(@Nullable Vector other) {
        return (other != null) && (this.x == other.x && this.y == other.y);
    }
}

