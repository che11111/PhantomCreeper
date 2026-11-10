package dev.che1s.phantomcreeper.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从 Bukkit 配置读取并校验不可变设置对象。
 */
final class ConfigSettingsReader {
    private ConfigSettingsReader() {
    }

    static PluginSettings read(FileConfiguration config) {
        double acquireRange = positive(config.getDouble("targeting.acquire-range", 48.0), 48.0);
        double loseRange = Math.max(
                acquireRange,
                positive(config.getDouble("targeting.lose-range", 64.0), 64.0)
        );
        double cruiseSpeed = positive(config.getDouble("movement.cruise-speed", 0.32), 0.32);
        double chaseSpeed = positive(config.getDouble("movement.chase-speed", 0.48), 0.48);
        double maxSpeed = Math.max(
                positive(config.getDouble("movement.max-speed", 0.68), 0.68),
                Math.max(cruiseSpeed, chaseSpeed)
        );
        double triggerRadius = positive(config.getDouble("combat.trigger-radius", 2.8), 2.8);
        double fuseCancelRadius = Math.max(
                triggerRadius,
                positive(config.getDouble("combat.fuse-cancel-radius", 7.0), 7.0)
        );
        double minSpawnDistance = positive(
                config.getDouble("natural-spawn.spawn-area.min-distance", 18.0), 18.0);
        double maxSpawnDistance = positive(
                config.getDouble("natural-spawn.spawn-area.max-distance", 32.0), 32.0);
        double minHeightOffset = finite(
                config.getDouble("natural-spawn.spawn-area.min-height-offset", 6.0), 6.0);
        double maxHeightOffset = finite(
                config.getDouble("natural-spawn.spawn-area.max-height-offset", 12.0), 12.0);

        return new PluginSettings(
                config.getString("language", "zh_cn").toLowerCase(Locale.ROOT),
                config.getBoolean("bstats-enabled", true),
                new PluginSettings.Model(
                        nonBlank(config.getString("model.engine", "auto"), "auto")
                                .toLowerCase(Locale.ROOT),
                        nonBlank(config.getString("model.name", "phantom_creeper"), "phantom_creeper"),
                        positive(config.getDouble("model.scale", 1.25), 1.25),
                        nonBlank(config.getString("model.flap-animation", "flap"), "flap"),
                        config.getBoolean("model.modelengine-auto-install-blueprint", true),
                        config.getBoolean("model.keep-base-phantom-visible-if-model-fails", true)
                ),
                new PluginSettings.Targeting(
                        acquireRange,
                        loseRange,
                        config.getBoolean("targeting.ignore-creative", true),
                        config.getBoolean("targeting.ignore-spectator", true),
                        config.getBoolean("targeting.require-line-of-sight", false)
                ),
                new PluginSettings.Movement(
                        cruiseSpeed,
                        chaseSpeed,
                        maxSpeed,
                        fraction(config.getDouble("movement.acceleration", 0.20), 0.20),
                        finite(config.getDouble("movement.cruise-height", 6.0), 6.0),
                        finite(config.getDouble("movement.strike-height", 1.2), 1.2),
                        positive(config.getDouble("movement.close-range", 8.0), 8.0),
                        positive(config.getDouble("movement.waypoint-radius", 3.5), 3.5),
                        positive(config.getDouble("movement.max-distance-from-home", 72.0), 72.0),
                        positive(config.getDouble("movement.minimum-separation", 6.0), 6.0)
                ),
                new PluginSettings.Combat(
                        positive(config.getDouble("combat.max-health", 24.0), 24.0),
                        triggerRadius,
                        nonNegative(config.getInt("combat.fuse-ticks", 30), 30),
                        fuseCancelRadius,
                        new PluginSettings.Explosion(
                                config.getBoolean("combat.explosion.break-blocks", false),
                                positive(config.getDouble("combat.explosion.radius", 6.0), 6.0),
                                nonNegative(config.getDouble("combat.explosion.damage", 20.0), 20.0),
                                config.getBoolean("combat.explosion.damage-falloff", true),
                                config.getBoolean("combat.explosion.set-fire", false),
                                config.getBoolean("combat.explosion.damage-self", false)
                        ),
                        nonNegative(config.getInt("combat.drop-phantom-membrane", 2), 2),
                        nonNegative(config.getInt("combat.drop-gunpowder", 2), 2),
                        nonNegative(config.getInt("combat.dropped-experience", 20), 20)
                ),
                new PluginSettings.NaturalSpawn(
                        config.getBoolean("natural-spawn.enabled", true),
                        positive(config.getLong("natural-spawn.check-interval-ticks", 200L), 200L),
                        nonNegative(config.getLong("natural-spawn.night-start-time", 13000L), 13000L),
                        nonNegative(config.getLong("natural-spawn.night-end-time", 23000L), 23000L),
                        config.getBoolean("natural-spawn.only-overworld", true),
                        nonNegative(config.getInt("natural-spawn.max-active", 10), 10),
                        nonNegative(config.getInt("natural-spawn.max-tracked", 20), 20),
                        new PluginSettings.SpawnArea(
                                Math.min(minSpawnDistance, maxSpawnDistance),
                                Math.max(minSpawnDistance, maxSpawnDistance),
                                Math.min(minHeightOffset, maxHeightOffset),
                                Math.max(minHeightOffset, maxHeightOffset)
                        ),
                        new PluginSettings.DifficultySpawn(
                                config.getBoolean("natural-spawn.nightly-difficulty-spawn.enabled", true),
                                positive(config.getInt(
                                        "natural-spawn.nightly-difficulty-spawn.spawn-count", 1), 1),
                                fraction(config.getDouble(
                                        "natural-spawn.nightly-difficulty-spawn.chances.easy", 0.05), 0.05),
                                fraction(config.getDouble(
                                        "natural-spawn.nightly-difficulty-spawn.chances.normal", 0.10), 0.10),
                                fraction(config.getDouble(
                                        "natural-spawn.nightly-difficulty-spawn.chances.hard", 0.20), 0.20)
                        ),
                        new PluginSettings.PhantomThresholdSpawn(
                                config.getBoolean("natural-spawn.phantom-threshold-spawn.enabled", true),
                                positive(config.getDouble(
                                        "natural-spawn.phantom-threshold-spawn.scan-radius", 32.0), 32.0),
                                phantomThresholds(config)
                        ),
                        List.copyOf(config.getStringList("natural-spawn.worlds"))
                ),
                new PluginSettings.Safety(
                        config.getBoolean("safety.remove-on-chunk-unload", true),
                        nonNegative(config.getInt("safety.despawn-after-ticks", 12000), 12000)
                )
        );
    }

    private static List<PluginSettings.PhantomThreshold> phantomThresholds(
            FileConfiguration config) {
        List<Map<?, ?>> values = config.getMapList(
                "natural-spawn.phantom-threshold-spawn.triggers");
        if (values.isEmpty()) {
            return List.of(
                    new PluginSettings.PhantomThreshold(3, 1),
                    new PluginSettings.PhantomThreshold(6, 2)
            );
        }
        return values.stream()
                .map(value -> new PluginSettings.PhantomThreshold(
                        configInt(value.get("phantom-count"), 1),
                        configInt(value.get("spawn-count"), 1)
                ))
                .filter(value -> value.phantomCount() > 0 && value.spawnCount() > 0)
                .sorted((left, right) -> Integer.compare(
                        left.phantomCount(), right.phantomCount()))
                .toList();
    }

    private static int configInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static long positive(long value, long fallback) {
        return value > 0L ? value : fallback;
    }

    private static int nonNegative(int value, int fallback) {
        return value >= 0 ? value : fallback;
    }

    private static long nonNegative(long value, long fallback) {
        return value >= 0L ? value : fallback;
    }

    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double fraction(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : fallback;
    }
}
