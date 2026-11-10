package dev.che1s.phantomcreeper.stats;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsServiceTest {
    @Test
    void statisticsPersistAcrossReopen() throws Exception {
        Path directory = Files.createTempDirectory("phantomcreeper-stats");
        Path database = directory.resolve("stats.db");
        UUID playerId = UUID.randomUUID();

        try (StatsService stats = new StatsService(database, _ -> {
        })) {
            stats.incrementKills(playerId, "Player");
            stats.incrementKills(playerId, "Player");
            stats.incrementDeaths(playerId, "Player");
            assertEquals(2L, stats.get(playerId).kills());
            assertEquals(1L, stats.get(playerId).deaths());
        }

        try (StatsService reopened = new StatsService(database, _ -> {
        })) {
            assertEquals(2L, reopened.get(playerId).kills());
            assertEquals(1L, reopened.get(playerId).deaths());
        }
    }
}
