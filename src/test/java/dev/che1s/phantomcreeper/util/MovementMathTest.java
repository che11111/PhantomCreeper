package dev.che1s.phantomcreeper.util;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementMathTest {
    @Test
    void steeringNeverExceedsConfiguredSpeed() {
        Vector velocity = MovementMath.steer(
                new Vector(20.0, -8.0, 3.0),
                new Vector(40.0, 10.0, -5.0),
                0.68,
                1.0
        );

        assertTrue(velocity.length() <= 0.6800001);
        assertTrue(velocity.getX() > 0.0);
    }

    @Test
    void accelerationMovesTowardDesiredDirection() {
        Vector velocity = MovementMath.steer(
                new Vector(0.0, 0.0, 0.0),
                new Vector(10.0, 0.0, 0.0),
                0.5,
                0.2
        );

        assertEquals(0.1, velocity.getX(), 1.0E-8);
        assertEquals(0.0, velocity.getY(), 1.0E-8);
        assertEquals(0.0, velocity.getZ(), 1.0E-8);
    }

    @Test
    void triggerUsesThreeDimensionalDistance() {
        assertTrue(MovementMath.withinTrigger(2.0, 1.0, 1.0, 2.8));
        assertFalse(MovementMath.withinTrigger(3.0, 0.0, 0.0, 2.8));
    }

    @Test
    void yawIsNormalizedForModelRotation() {
        assertEquals(-170.0, MovementMath.clampYaw(190.0), 1.0E-8);
        assertEquals(170.0, MovementMath.clampYaw(-190.0), 1.0E-8);
        assertEquals(0.0, MovementMath.clampYaw(720.0), 1.0E-8);
    }
}
