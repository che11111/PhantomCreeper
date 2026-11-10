package dev.che1s.phantomcreeper.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExplosionMathTest {
    @Test
    void linearFalloffReachesConfiguredCenterDamage() {
        assertEquals(20.0F, ExplosionMath.damageAtDistance(20.0, 6.0, true, 0.0));
        assertEquals(10.0F, ExplosionMath.damageAtDistance(20.0, 6.0, true, 3.0));
        assertEquals(0.0F, ExplosionMath.damageAtDistance(20.0, 6.0, true, 6.0));
    }

    @Test
    void fixedDamageCanBeUsedWithoutFalloff() {
        assertEquals(8.0F, ExplosionMath.damageAtDistance(8.0, 4.0, false, 3.9));
    }
}
