package dev.che1s.phantomcreeper.spawn;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightlyAttemptTrackerTest {
    @Test
    void attemptsResetWhenWorldOrDayChanges() {
        NightlyAttemptTracker tracker = new NightlyAttemptTracker();
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        NightlyAttemptTracker.State first = tracker.state(playerId, worldId, 10);
        tracker.markDifficulty(playerId, first);
        tracker.markThreshold(playerId, first);
        NightlyAttemptTracker.State completed = tracker.state(playerId, worldId, 10);
        assertTrue(completed.difficultyAttempted());
        assertTrue(completed.thresholdAttempted());

        NightlyAttemptTracker.State nextDay = tracker.state(playerId, worldId, 11);
        assertFalse(nextDay.difficultyAttempted());
        assertFalse(nextDay.thresholdAttempted());

        NightlyAttemptTracker.State nextWorld = tracker.state(playerId, UUID.randomUUID(), 11);
        assertFalse(nextWorld.difficultyAttempted());
    }

    @Test
    void offlinePlayersArePruned() {
        NightlyAttemptTracker tracker = new NightlyAttemptTracker();
        UUID playerId = UUID.randomUUID();
        tracker.state(playerId, UUID.randomUUID(), 1);

        tracker.retainPlayers(Set.of());
        assertFalse(tracker.state(playerId, UUID.randomUUID(), 1).difficultyAttempted());
    }
}
