package dev.che1s.phantomcreeper.command;

import dev.che1s.phantomcreeper.lang.Language;
import dev.che1s.phantomcreeper.mob.PhantomCreeperManager;
import dev.che1s.phantomcreeper.model.PhantomModel;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import dev.che1s.phantomcreeper.spawn.NaturalSpawner;
import dev.che1s.phantomcreeper.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class PhantomCreeperCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PhantomCreeperManager manager;
    private final NaturalSpawner spawner;
    private final PhantomModel model;
    private final StatsService stats;
    private final Language language;
    private final BooleanSupplier reload;

    public PhantomCreeperCommand(
            JavaPlugin plugin,
            PhantomCreeperManager manager,
            NaturalSpawner spawner,
            PhantomModel model,
            StatsService stats,
            Language language,
            BooleanSupplier reload) {
        this.plugin = plugin;
        this.manager = manager;
        this.spawner = spawner;
        this.model = model;
        this.stats = stats;
        this.language = language;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            language.send(sender, "command.usage", Map.of());
            return true;
        }
        String subcommand = args[0].toLowerCase();
        if (!subcommand.equals("stats") && !sender.hasPermission("phantomcreeper.admin")) {
            language.send(sender, "command.no-permission", Map.of());
            return true;
        }
        return switch (subcommand) {
            case "spawn" -> spawn(sender, args);
            case "removeall" -> removeAll(sender);
            case "reload" -> reload(sender);
            case "info" -> info(sender);
            case "stats" -> stats(sender);
            case "engine" -> engine(sender, args);
            default -> {
                language.send(sender, "command.usage", Map.of());
                yield true;
            }
        };
    }

    private boolean spawn(CommandSender sender, String[] args) {
        Player target = null;
        Location spawn;
        UUID preferredTarget;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                language.send(sender, "command.player-not-found", Map.of("player", args[1]));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        }

        if (target != null) {
            Location origin = target.getLocation().clone();
            spawn = origin.clone()
                    .add(origin.getDirection().multiply(12.0))
                    .add(0.0, 6.0, 0.0);
            preferredTarget = target.getUniqueId();
        } else {
            if (Bukkit.getWorlds().isEmpty()) {
                language.send(sender, "command.spawn-failed", Map.of());
                return true;
            }
            World world = Bukkit.getWorlds().getFirst();
            spawn = world.getSpawnLocation().clone().add(12.0, 12.0, 12.0);
            preferredTarget = null;
        }

        Location scheduleAt = target == null ? spawn : target.getLocation();
        Location finalSpawn = spawn;
        UUID finalTarget = preferredTarget;
        World world = finalSpawn.getWorld();
        if (world == null) {
            language.send(sender, "command.spawn-failed", Map.of());
            return true;
        }
        int chunkX = finalSpawn.getBlockX() >> 4;
        int chunkZ = finalSpawn.getBlockZ() >> 4;
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(ignored -> {
            PlatformScheduler.runAtLocation(plugin, finalSpawn, () -> {
                if (manager.spawnNow(finalSpawn, finalTarget) == null) {
                    language.send(sender, "command.spawn-failed", Map.of());
                } else {
                    language.send(sender, "command.spawn-success", Map.of());
                }
            });
        }).exceptionally(exception -> {
            PlatformScheduler.runAtLocation(plugin, scheduleAt, () -> language.send(
                    sender,
                    "command.spawn-failed",
                    Map.of()
            ));
            return null;
        });
        return true;
    }

    private boolean removeAll(CommandSender sender) {
        int count = manager.size();
        manager.removeAll();
        language.send(sender, "command.removed", Map.of("count", Integer.toString(count)));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (reload.getAsBoolean()) {
            spawner.restart();
            language.send(sender, "command.reloaded", Map.of());
        } else {
            language.send(sender, "command.reload-failed", Map.of());
        }
        return true;
    }

    private boolean info(CommandSender sender) {
        var first = manager.firstController();
        language.send(sender, "command.info", Map.of(
                "count", Integer.toString(manager.size()),
                "natural", Integer.toString(manager.naturalCount()),
                "natural_limit", Integer.toString(manager.naturalLimit()),
                "model", language.plain(
                        model.isLoaded() ? "status.loaded" : "status.waiting",
                        Map.of()
                ),
                "engine", model.backendName(),
                "position", first == null ? "-" : first.positionStatus(),
                "velocity", first == null ? "-" : first.velocityStatus(),
                "health", first == null ? "-" : first.healthStatus(),
                "hitboxes", first == null ? "0" : Integer.toString(first.hitboxCount())
        ));
        return true;
    }

    private boolean stats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            language.send(sender, "command.player-only", Map.of());
            return true;
        }
        if (!player.hasPermission("phantomcreeper.stats")) {
            language.send(player, "command.no-permission", Map.of());
            return true;
        }
        StatsService.PlayerStats playerStats = stats.get(player.getUniqueId());
        language.send(player, "command.stats", Map.of(
                "kills", Long.toString(playerStats.kills()),
                "deaths", Long.toString(playerStats.deaths())
        ));
        return true;
    }

    private boolean engine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            language.send(sender, "command.engine-usage", Map.of());
            return true;
        }
        String requested = args[1].toLowerCase();
        if (!model.switchBackend(requested)) {
            language.send(sender, "command.engine-unavailable", Map.of(
                    "engine", args[1],
                    "available", String.join(", ", model.availableBackends())
            ));
            return true;
        }
        manager.reattachAll();
        language.send(sender, "command.engine-switched", Map.of(
                "engine", model.backendName()
        ));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(List.of("stats"));
            if (sender.hasPermission("phantomcreeper.admin")) {
                commands.addAll(List.of("spawn", "removeall", "reload", "info", "engine"));
            }
            return filter(commands, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("engine")) {
            return filter(model.availableBackends().stream().toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase();
        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(lower))
                .sorted()
                .toList();
    }
}
