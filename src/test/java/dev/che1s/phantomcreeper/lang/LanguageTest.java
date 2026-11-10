package dev.che1s.phantomcreeper.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageTest {
    @Test
    void missingLocalKeyUsesBundledDefault() {
        YamlConfiguration local = new YamlConfiguration();
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("command.stats", "统计：<kills>/<deaths>");
        local.setDefaults(bundled);

        assertEquals(
                "统计：<kills>/<deaths>",
                Language.resolve(local, "command.stats")
        );
    }

    @Test
    void completelyMissingKeyFallsBackToKeyName() {
        assertEquals(
                "command.missing",
                Language.resolve(new YamlConfiguration(), "command.missing")
        );
    }
}
