package dev.che1s.phantomcreeper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSettingsReaderTest {
    @Test
    void relatedRangesAreNormalized() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("targeting.acquire-range", 64.0);
        config.set("targeting.lose-range", 32.0);
        config.set("movement.cruise-speed", 0.5);
        config.set("movement.chase-speed", 0.8);
        config.set("movement.max-speed", 0.1);
        config.set("combat.trigger-radius", 5.0);
        config.set("combat.fuse-cancel-radius", 2.0);
        config.set("natural-spawn.spawn-area.min-distance", 40.0);
        config.set("natural-spawn.spawn-area.max-distance", 20.0);
        config.set("natural-spawn.spawn-area.min-height-offset", 12.0);
        config.set("natural-spawn.spawn-area.max-height-offset", 6.0);

        PluginSettings settings = ConfigSettingsReader.read(config);

        assertEquals(64.0, settings.targeting().loseRange());
        assertEquals(0.8, settings.movement().maxSpeed());
        assertEquals(5.0, settings.combat().fuseCancelRadius());
        assertEquals(20.0, settings.naturalSpawn().spawnArea().minDistance());
        assertEquals(40.0, settings.naturalSpawn().spawnArea().maxDistance());
        assertEquals(6.0, settings.naturalSpawn().spawnArea().minHeightOffset());
        assertEquals(12.0, settings.naturalSpawn().spawnArea().maxHeightOffset());
    }
}
