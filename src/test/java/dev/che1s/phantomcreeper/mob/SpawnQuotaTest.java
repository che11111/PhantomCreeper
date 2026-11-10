package dev.che1s.phantomcreeper.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnQuotaTest {
    @Test
    void reservationsEnforceNaturalLimitUntilReleased() {
        SpawnQuota quota = new SpawnQuota();

        assertTrue(quota.reserve(PhantomCreeperManager.SpawnSource.NATURAL, 10, 2));
        assertTrue(quota.reserve(PhantomCreeperManager.SpawnSource.NATURAL, 10, 2));
        assertFalse(quota.reserve(PhantomCreeperManager.SpawnSource.NATURAL, 10, 2));
        assertEquals(2, quota.naturalReservations());

        quota.release(PhantomCreeperManager.SpawnSource.NATURAL);
        assertTrue(quota.reserve(PhantomCreeperManager.SpawnSource.NATURAL, 10, 2));
    }

    @Test
    void commandSpawnsUseTrackedLimitButNotNaturalLimit() {
        SpawnQuota quota = new SpawnQuota();

        assertTrue(quota.reserve(PhantomCreeperManager.SpawnSource.NATURAL, 1, 10));
        assertFalse(quota.reserve(PhantomCreeperManager.SpawnSource.COMMAND, 1, 10));
        assertTrue(quota.reserve(PhantomCreeperManager.SpawnSource.COMMAND, 2, 10));
        assertEquals(1, quota.naturalReservations());
    }
}
