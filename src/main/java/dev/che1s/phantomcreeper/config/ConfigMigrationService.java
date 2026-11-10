package dev.che1s.phantomcreeper.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

/**
 * 补齐新增配置，并迁移历史版本中的旧字段。
 */
final class ConfigMigrationService {
    private ConfigMigrationService() {
    }

    static boolean migrate(FileConfiguration config) {
        boolean changed = false;
        changed |= setIfMissing(config, "movement.minimum-separation", 6.0);
        changed |= setIfMissing(config, "combat.fuse-cancel-radius", 7.0);
        changed |= setIfMissing(config, "model.engine", "auto");
        changed |= setIfMissing(config, "model.modelengine-auto-install-blueprint", true);
        changed |= setIfMissing(config, "bstats-enabled", true);
        changed |= migrateLegacyExplosionConfig(config);
        changed |= migrateLegacySpawnConfig(config);
        return changed;
    }

    private static boolean setIfMissing(
            FileConfiguration config,
            String path,
            Object value) {
        if (config.isSet(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private static boolean migrateLegacyExplosionConfig(FileConfiguration config) {
        if (config.isSet("combat.explosion.radius")) {
            return false;
        }
        double legacyPower = positive(config.getDouble("combat.explosion-power", 3.0), 3.0);
        config.set("combat.explosion.radius", legacyPower * 2.0);
        config.set("combat.explosion.damage", 20.0);
        config.set("combat.explosion.damage-falloff", true);
        config.set("combat.explosion.break-blocks", config.getBoolean("combat.break-blocks", false));
        config.set("combat.explosion.set-fire", config.getBoolean("combat.set-fire", false));
        config.set("combat.explosion.damage-self", config.getBoolean("combat.damage-self", false));
        config.set("combat.explosion-power", null);
        config.set("combat.break-blocks", null);
        config.set("combat.set-fire", null);
        config.set("combat.damage-self", null);
        return true;
    }

    private static boolean migrateLegacySpawnConfig(FileConfiguration config) {
        boolean changed = false;
        if (!config.isSet("natural-spawn.max-active")) {
            config.set("natural-spawn.max-active", 10);
            changed = true;
        }
        if (config.isSet("natural-spawn.check-interval-ticks")) {
            return changed;
        }
        config.set("natural-spawn.check-interval-ticks", 200L);
        config.set("natural-spawn.night-start-time", 13000L);
        config.set("natural-spawn.night-end-time", 23000L);
        config.set("natural-spawn.spawn-area.min-distance",
                positive(config.getDouble("natural-spawn.min-distance", 18.0), 18.0));
        config.set("natural-spawn.spawn-area.max-distance",
                positive(config.getDouble("natural-spawn.max-distance", 32.0), 32.0));
        config.set("natural-spawn.spawn-area.min-height-offset",
                finite(config.getDouble("natural-spawn.min-height-offset", 6.0), 6.0));
        config.set("natural-spawn.spawn-area.max-height-offset",
                finite(config.getDouble("natural-spawn.max-height-offset", 12.0), 12.0));
        config.set("natural-spawn.nightly-difficulty-spawn.enabled", true);
        config.set("natural-spawn.nightly-difficulty-spawn.spawn-count", 1);
        config.set("natural-spawn.nightly-difficulty-spawn.chances.easy", 0.05);
        config.set("natural-spawn.nightly-difficulty-spawn.chances.normal", 0.10);
        config.set("natural-spawn.nightly-difficulty-spawn.chances.hard", 0.20);
        config.set("natural-spawn.phantom-threshold-spawn.enabled", true);
        config.set("natural-spawn.phantom-threshold-spawn.scan-radius", 32.0);
        config.set("natural-spawn.phantom-threshold-spawn.triggers", List.of(
                Map.of("phantom-count", 3, "spawn-count", 1),
                Map.of("phantom-count", 6, "spawn-count", 2)
        ));
        config.set("natural-spawn.interval-ticks", null);
        config.set("natural-spawn.chance", null);
        config.set("natural-spawn.only-night-or-thunder", null);
        config.set("natural-spawn.max-near-player", null);
        config.set("natural-spawn.min-distance", null);
        config.set("natural-spawn.max-distance", null);
        config.set("natural-spawn.min-height-offset", null);
        config.set("natural-spawn.max-height-offset", null);
        return true;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
