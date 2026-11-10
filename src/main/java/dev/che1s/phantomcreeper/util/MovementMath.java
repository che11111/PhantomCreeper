package dev.che1s.phantomcreeper.util;

import org.bukkit.util.Vector;

public final class MovementMath {
    private MovementMath() {
    }

    public static Vector steer(Vector current, Vector toTarget, double maxSpeed, double acceleration) {
        if (toTarget.lengthSquared() < 1.0E-8) {
            return current.clone();
        }
        double speed = Math.max(0.0, maxSpeed);
        double easing = Math.max(0.0, Math.min(1.0, acceleration));
        Vector desired = toTarget.clone().normalize().multiply(speed);
        Vector velocity = current.clone().multiply(1.0 - easing).add(desired.multiply(easing));
        if (velocity.lengthSquared() > speed * speed) {
            velocity.normalize().multiply(speed);
        }
        return velocity;
    }

    public static boolean withinTrigger(double dx, double dy, double dz, double radius) {
        double safeRadius = Math.max(0.0, radius);
        return dx * dx + dy * dy + dz * dz <= safeRadius * safeRadius;
    }

    public static double clampYaw(double degrees) {
        double result = degrees % 360.0;
        return result < -180.0 ? result + 360.0 : result > 180.0 ? result - 360.0 : result;
    }
}
