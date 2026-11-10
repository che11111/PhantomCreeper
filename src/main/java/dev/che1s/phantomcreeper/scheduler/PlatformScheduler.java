package dev.che1s.phantomcreeper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class PlatformScheduler {
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private PlatformScheduler() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static TaskHandle globalTimer(
            JavaPlugin plugin, long initialDelayTicks, long periodTicks, Runnable runnable) {
        if (FOLIA) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    ignored -> runnable.run(),
                    Math.max(1L, initialDelayTicks),
                    Math.max(1L, periodTicks)
            );
            return foliaHandle(task);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                plugin, runnable, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        return task::cancel;
    }

    public static TaskHandle runAtLocation(JavaPlugin plugin, Location location, Runnable runnable) {
        Objects.requireNonNull(location.getWorld());
        if (FOLIA) {
            ScheduledTask task = Bukkit.getRegionScheduler().run(
                    plugin, location, ignored -> runnable.run());
            return foliaHandle(task);
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    public static TaskHandle laterGlobal(
            JavaPlugin plugin,
            long delayTicks,
            Runnable runnable) {
        if (FOLIA) {
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(
                    plugin,
                    ignored -> runnable.run(),
                    Math.max(1L, delayTicks)
            );
            return foliaHandle(task);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                runnable,
                Math.max(1L, delayTicks)
        );
        return task::cancel;
    }

    public static TaskHandle laterAtLocation(
            JavaPlugin plugin,
            Location location,
            long delayTicks,
            Runnable runnable) {
        Objects.requireNonNull(location.getWorld());
        if (FOLIA) {
            ScheduledTask task = Bukkit.getRegionScheduler().runDelayed(
                    plugin,
                    location,
                    ignored -> runnable.run(),
                    Math.max(1L, delayTicks)
            );
            return foliaHandle(task);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                runnable,
                Math.max(1L, delayTicks)
        );
        return task::cancel;
    }

    public static TaskHandle runOnEntity(JavaPlugin plugin, Entity entity, Runnable runnable) {
        if (FOLIA) {
            AtomicReference<ScheduledTask> reference = new AtomicReference<>();
            reference.set(entity.getScheduler().run(plugin, ignored -> runnable.run(), () -> {
            }));
            return () -> cancel(reference.get());
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    public static TaskHandle timerOnEntity(
            JavaPlugin plugin,
            Entity entity,
            long initialDelayTicks,
            long periodTicks,
            Runnable runnable) {
        if (FOLIA) {
            ScheduledTask task = entity.getScheduler().runAtFixedRate(
                    plugin,
                    ignored -> {
                        if (entity.isValid()) {
                            runnable.run();
                        }
                    },
                    () -> {
                    },
                    Math.max(1L, initialDelayTicks),
                    Math.max(1L, periodTicks)
            );
            return foliaHandle(task);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                plugin, runnable, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        return task::cancel;
    }

    public static TaskHandle laterOnEntity(
            JavaPlugin plugin, Entity entity, long delayTicks, Runnable runnable) {
        if (FOLIA) {
            ScheduledTask task = entity.getScheduler().runDelayed(
                    plugin, ignored -> runnable.run(), () -> {
                    }, Math.max(1L, delayTicks));
            return foliaHandle(task);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1L, delayTicks));
        return task::cancel;
    }

    private static TaskHandle foliaHandle(ScheduledTask task) {
        return () -> cancel(task);
    }

    private static void cancel(ScheduledTask task) {
        if (task != null && task.getExecutionState() == ScheduledTask.ExecutionState.RUNNING) {
            task.cancel();
        }
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }
}
