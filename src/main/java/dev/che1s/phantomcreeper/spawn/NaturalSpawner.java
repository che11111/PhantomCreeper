package dev.che1s.phantomcreeper.spawn;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.mob.PhantomCreeperManager;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class NaturalSpawner {
    private final JavaPlugin plugin;
    private final Supplier<PluginSettings> settings;
    private final PhantomCreeperManager manager;
    private final NightlyAttemptTracker nightlyAttempts = new NightlyAttemptTracker();
    private PlatformScheduler.TaskHandle task;

    public NaturalSpawner(
            JavaPlugin plugin,
            Supplier<PluginSettings> settings,
            PhantomCreeperManager manager) {
        this.plugin = plugin;
        this.settings = settings;
        this.manager = manager;
    }

    public void restart() {
        stop();
        PluginSettings.NaturalSpawn current = settings.get().naturalSpawn();
        if (!current.enabled()) {
            return;
        }
        task = PlatformScheduler.globalTimer(
                plugin,
                current.checkIntervalTicks(),
                current.checkIntervalTicks(),
                this::checkPlayers
        );
    }

    public void stop() {
        PlatformScheduler.TaskHandle current = task;
        task = null;
        if (current != null) {
            current.cancel();
        }
    }

    private void checkPlayers() {
        PluginSettings current = settings.get();
        if (!current.naturalSpawn().enabled()
                || manager.size() >= Math.max(1, current.naturalSpawn().maxTracked())
                || manager.naturalCount() >= current.naturalSpawn().maxActive()) {
            return;
        }
        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
        Set<UUID> onlineIds = candidates.stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toUnmodifiableSet());
        // 夜间状态只对在线玩家有意义，避免玩家退出后长期占用内存。
        nightlyAttempts.retainPlayers(onlineIds);
        Collections.shuffle(candidates);
        for (Player player : candidates) {
            PlatformScheduler.runOnEntity(plugin, player, () -> checkFor(player));
        }
    }

    private void checkFor(Player player) {
        if (!player.isOnline()) {
            return;
        }
        PluginSettings current = settings.get();
        PluginSettings.NaturalSpawn spawn = current.naturalSpawn();
        World world = player.getWorld();
        if (!current.worldAllowed(world.getName())
                || (spawn.onlyOverworld() && world.getEnvironment() != World.Environment.NORMAL)
                || !isNight(world, spawn)
                || manager.size() >= Math.max(1, spawn.maxTracked())
                || manager.naturalCount() >= spawn.maxActive()) {
            return;
        }

        long day = Math.floorDiv(world.getFullTime(), 24000L);
        NightlyAttemptTracker.State state = nightlyAttempts.state(
                player.getUniqueId(),
                world.getUID(),
                day
        );
        if (state.difficultyAttempted() && state.thresholdAttempted()) {
            return;
        }

        PluginSettings.DifficultySpawn difficulty = spawn.difficultySpawn();
        if (difficulty.enabled() && !state.difficultyAttempted()) {
            state = state.withDifficultyAttempted();
            nightlyAttempts.markDifficulty(player.getUniqueId(), state);
            double chance = chance(world.getDifficulty(), difficulty);
            if (chance > 0.0 && ThreadLocalRandom.current().nextDouble() < chance) {
                spawnBatch(player, difficulty.spawnCount());
            }
        }

        PluginSettings.PhantomThresholdSpawn thresholdSpawn = spawn.phantomThresholdSpawn();
        if (thresholdSpawn.enabled() && !state.thresholdAttempted()) {
            int phantomCount = countNearbyPhantoms(player, thresholdSpawn.scanRadius());
            PluginSettings.PhantomThreshold matched = thresholdSpawn.triggers().stream()
                    .filter(trigger -> phantomCount >= trigger.phantomCount())
                    .max((left, right) -> Integer.compare(
                            left.phantomCount(), right.phantomCount()))
                    .orElse(null);
            if (matched != null) {
                // 仅在真正达到阈值并执行生成时消耗当晚机会；
                // 未达到阈值时继续检查，便于后续幻翼数量增长后触发。
                nightlyAttempts.markThreshold(player.getUniqueId(), state);
                spawnBatch(player, matched.spawnCount());
            }
        }
    }

    private int countNearbyPhantoms(Player player, double radius) {
        Location center = player.getLocation();
        return player.getWorld().getNearbyEntities(
                        center,
                        radius,
                        radius,
                        radius,
                        entity -> entity.getType() == EntityType.PHANTOM
                )
                .size();
    }

    private void spawnBatch(Player player, int requestedCount) {
        PluginSettings current = settings.get();
        PluginSettings.NaturalSpawn spawn = current.naturalSpawn();
        if (!spawn.enabled()) {
            return;
        }
        int count = Math.max(1, requestedCount);
        double baseAngle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        for (int index = 0; index < count; index++) {
            if (manager.size() >= Math.max(1, spawn.maxTracked())) {
                return;
            }
            if (manager.naturalCount() >= spawn.maxActive()) {
                return;
            }
            double angle = baseAngle
                    + index * Math.PI * 2.0 / count
                    + ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
            spawnOne(player, spawn.spawnArea(), angle);
        }
    }

    private void spawnOne(
            Player player,
            PluginSettings.SpawnArea area,
            double angle) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        double maxDistance = Math.max(area.minDistance(), area.maxDistance());
        double minDistance = Math.min(area.minDistance(), maxDistance);
        double distance = random(minDistance, maxDistance);
        double x = origin.getX() + Math.cos(angle) * distance;
        double z = origin.getZ() + Math.sin(angle) * distance;
        double minOffset = Math.min(area.minHeightOffset(), area.maxHeightOffset());
        double maxOffset = Math.max(area.minHeightOffset(), area.maxHeightOffset());
        double y = origin.getY() + random(minOffset, maxOffset);
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return;
        }
        y = Math.max(world.getMinHeight() + 3.0, Math.min(world.getMaxHeight() - 3.0, y));
        Location spawnLocation = new Location(
                world,
                x,
                y,
                z,
                ThreadLocalRandom.current().nextFloat() * 360.0F,
                0.0F
        );
        PlatformScheduler.runAtLocation(plugin, spawnLocation, () -> {
            if (world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                manager.spawnNow(
                        spawnLocation,
                        player.getUniqueId(),
                        PhantomCreeperManager.SpawnSource.NATURAL
                );
            }
        });
    }

    private static boolean isNight(World world, PluginSettings.NaturalSpawn spawn) {
        long time = world.getTime();
        long start = spawn.nightStartTime();
        long end = spawn.nightEndTime();
        if (start <= end) {
            return time >= start && time < end;
        }
        return time >= start || time < end;
    }

    private static double chance(
            Difficulty difficulty,
            PluginSettings.DifficultySpawn settings) {
        return switch (difficulty) {
            case EASY -> settings.easyChance();
            case NORMAL -> settings.normalChance();
            case HARD -> settings.hardChance();
            default -> 0.0;
        };
    }

    private static double random(double min, double max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

}
