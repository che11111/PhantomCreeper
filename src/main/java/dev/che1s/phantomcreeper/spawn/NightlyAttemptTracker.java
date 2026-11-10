package dev.che1s.phantomcreeper.spawn;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录每名玩家在当前世界、当前夜晚已经执行的生成机制。
 */
final class NightlyAttemptTracker {
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    State state(UUID playerId, UUID worldId, long day) {
        return states.compute(playerId, (uuid, existing) -> {
            if (existing == null
                    || existing.day() != day
                    || !existing.worldId().equals(worldId)) {
                return new State(worldId, day, false, false);
            }
            return existing;
        });
    }

    void markDifficulty(UUID playerId, State state) {
        states.compute(playerId, (uuid, existing) ->
                currentState(existing, state).withDifficultyAttempted());
    }

    void markThreshold(UUID playerId, State state) {
        states.compute(playerId, (uuid, existing) ->
                currentState(existing, state).withThresholdAttempted());
    }

    void retainPlayers(Set<UUID> onlinePlayers) {
        states.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
    }

    private static State currentState(State existing, State fallback) {
        if (existing != null
                && existing.day() == fallback.day()
                && existing.worldId().equals(fallback.worldId())) {
            return existing;
        }
        return fallback;
    }

    record State(
            UUID worldId,
            long day,
            boolean difficultyAttempted,
            boolean thresholdAttempted
    ) {
        State withDifficultyAttempted() {
            return new State(worldId, day, true, thresholdAttempted);
        }

        State withThresholdAttempted() {
            return new State(worldId, day, difficultyAttempted, true);
        }
    }
}
