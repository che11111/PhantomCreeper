package dev.che1s.phantomcreeper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationServiceTest {
    @Test
    void missingNewKeysAreAddedOnlyOnce() {
        YamlConfiguration config = new YamlConfiguration();

        assertTrue(ConfigMigrationService.migrate(config));
        assertFalse(ConfigMigrationService.migrate(config));
        assertEquals(true, config.getBoolean("bstats-enabled"));
        assertEquals(7.0, config.getDouble("combat.fuse-cancel-radius"));
        assertEquals("auto", config.getString("model.engine"));
    }
}
