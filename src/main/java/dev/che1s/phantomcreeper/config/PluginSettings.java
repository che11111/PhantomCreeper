package dev.che1s.phantomcreeper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public record PluginSettings(
        String language,
        boolean bstatsEnabled,
        Model model,
        Targeting targeting,
        Movement movement,
        Combat combat,
        NaturalSpawn naturalSpawn,
        Safety safety
) {
    public static PluginSettings load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        if (ConfigMigrationService.migrate(config)) {
            plugin.saveConfig();
        }
        return ConfigSettingsReader.read(config);
    }

    public boolean worldAllowed(String worldName) {
        return naturalSpawn.worlds().isEmpty() || naturalSpawn.worlds().contains(worldName);
    }

    public record Model(
            String engine,
            String name,
            double scale,
            String flapAnimation,
            boolean modelEngineAutoInstall,
            boolean fallbackVisible
    ) {
    }

    public record Targeting(
            double acquireRange,
            double loseRange,
            boolean ignoreCreative,
            boolean ignoreSpectator,
            boolean requireLineOfSight
    ) {
    }

    public record Movement(
            double cruiseSpeed,
            double chaseSpeed,
            double maxSpeed,
            double acceleration,
            double cruiseHeight,
            double strikeHeight,
            double closeRange,
            double waypointRadius,
            double maxDistanceFromHome,
            double minimumSeparation
    ) {
    }

    public record Combat(
            double maxHealth,
            double triggerRadius,
            int fuseTicks,
            double fuseCancelRadius,
            Explosion explosion,
            int phantomMembraneDrops,
            int gunpowderDrops,
            int experience
    ) {
    }

    public record Explosion(
            boolean breakBlocks,
            double radius,
            double damage,
            boolean damageFalloff,
            boolean setFire,
            boolean damageSelf
    ) {
    }

    public record NaturalSpawn(
            boolean enabled,
            long checkIntervalTicks,
            long nightStartTime,
            long nightEndTime,
            boolean onlyOverworld,
            int maxActive,
            int maxTracked,
            SpawnArea spawnArea,
            DifficultySpawn difficultySpawn,
            PhantomThresholdSpawn phantomThresholdSpawn,
            List<String> worlds
    ) {
    }

    public record SpawnArea(
            double minDistance,
            double maxDistance,
            double minHeightOffset,
            double maxHeightOffset
    ) {
    }

    public record DifficultySpawn(
            boolean enabled,
            int spawnCount,
            double easyChance,
            double normalChance,
            double hardChance
    ) {
    }

    public record PhantomThresholdSpawn(
            boolean enabled,
            double scanRadius,
            List<PhantomThreshold> triggers
    ) {
    }

    public record PhantomThreshold(
            int phantomCount,
            int spawnCount
    ) {
    }

    public record Safety(boolean removeOnChunkUnload, int despawnAfterTicks) {
    }
}
