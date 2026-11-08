package edu.msoe.envsim.utilities;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class MutableVector implements VectorBlueprint {
    private double x, y;
    private double angle = Double.NaN;

    public MutableVector(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Contract("_ -> new")
    public static @NotNull MutableVector fromAngle(double angle) {
        return new MutableVector(Math.cos(angle), Math.sin(angle));
    }


    /*
     * State Functions
     */

    public void turn(double change) {
        setAngle(getAngle() + change);
    }

    public void setAngle(double angle) {
        this.angle = angle;
        x = Math.cos(angle);
        y = Math.sin(angle);
    }

    public MutableVector scale(double scale) {
        x *= scale;
        y *= scale;
        return this;
    }

    public MutableVector add(@NotNull Vector other) {
        x += other.x();
        y += other.y();
        return this;
    }


    /*
     * Calculation Functions:
     */

    public double dot(@NotNull VectorBlueprint other) {
        return x() * other.x() + y() * other.y();
    }


    /*
     * Getters:
     */

    public double getAngle() {
        if  (Double.isNaN(angle)) {
            return Math.atan2(y, x);
        }
        return angle;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public Vector getImmutable() {
        return new Vector(x(), y());
    }
}
