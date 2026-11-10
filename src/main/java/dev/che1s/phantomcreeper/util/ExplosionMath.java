package dev.che1s.phantomcreeper.util;

public final class ExplosionMath {
    private ExplosionMath() {
    }

    public static float damageAtDistance(
            double maximumDamage,
            double radius,
            boolean falloff,
            double distance) {
        if (maximumDamage <= 0.0 || radius <= 0.0 || distance >= radius) {
            return 0.0F;
        }
        if (!falloff) {
            return (float) maximumDamage;
        }
        double factor = 1.0 - Math.max(0.0, distance) / radius;
        return (float) (maximumDamage * factor);
    }
}
