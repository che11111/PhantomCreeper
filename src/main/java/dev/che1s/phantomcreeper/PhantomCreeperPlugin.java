package dev.che1s.phantomcreeper;

import dev.che1s.phantomcreeper.command.PhantomCreeperCommand;
import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.lang.Language;
import dev.che1s.phantomcreeper.listener.CreatureListener;
import dev.che1s.phantomcreeper.mob.PhantomCreeperManager;
import dev.che1s.phantomcreeper.model.PhantomModel;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import dev.che1s.phantomcreeper.spawn.NaturalSpawner;
import dev.che1s.phantomcreeper.stats.StatsService;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class PhantomCreeperPlugin extends JavaPlugin {
    private volatile PluginSettings settings;
    private Language language;
    private PhantomModel model;
    private PhantomCreeperManager manager;
    private NaturalSpawner spawner;
    private StatsService stats;
    private Metrics metrics;
    private Object placeholderExpansion;

    @Override
    public void onEnable() {
        settings = PluginSettings.load(this);
        language = new Language(this);
        language.reload(settings.language());
        if (!PlatformScheduler.isFolia()) {
            getLogger().info(language.plain("log.paper-scheduler", Map.of()));
        } else {
            getLogger().info(language.plain("log.folia-scheduler", Map.of()));
        }
        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            getLogger().severe(language.plain("log.data-directory-failed", Map.of()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            stats = new StatsService(
                    getDataFolder().toPath().resolve("stats.db"),
                    exception -> getLogger().log(
                            java.util.logging.Level.SEVERE,
                            language.plain("log.stats-save-failed", Map.of()),
                            exception
                    )
            );
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    language.plain("log.stats-open-failed", Map.of()), exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        syncBstats(settings.bstatsEnabled());
        model = new PhantomModel(this, this::currentSettings, language);
        if (!model.hasBackend()) {
            getLogger().severe(language.plain(
                    "log.model-backend-missing",
                    Map.of("engine", settings.model().engine())
            ));
            stats.close();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        manager = new PhantomCreeperManager(this, this::currentSettings, language, model);
        spawner = new NaturalSpawner(this, this::currentSettings, manager);

        model.register(manager::onModelImported);
        getLogger().info(language.plain("log.model-backend-selected", Map.of(
                "engine", model.backendName()
        )));
        getServer().getPluginManager().registerEvents(
                new CreatureListener(manager, this::currentSettings, stats), this);
        registerPlaceholderApi();

        PluginCommand command = getCommand("phantomcreeper");
        if (command != null) {
            PhantomCreeperCommand executor = new PhantomCreeperCommand(
                    this, manager, spawner, model, stats, language, this::reloadAddon);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        spawner.restart();
        getLogger().info(language.plain("model.loading", Map.of()));
    }

    @Override
    public void onDisable() {
        if (spawner != null) {
            spawner.stop();
        }
        if (manager != null) {
            manager.shutdown();
        }
        if (model != null) {
            model.close();
        }
        unregisterPlaceholderApi();
        if (stats != null) {
            stats.close();
        }
        syncBstats(false);
    }

    private PluginSettings currentSettings() {
        return settings;
    }

    private boolean reloadAddon() {
        try {
            reloadConfig();
            PluginSettings loaded = PluginSettings.load(this);
            settings = loaded;
            language.reload(loaded.language());
            syncBstats(loaded.bstatsEnabled());
            return true;
        } catch (RuntimeException exception) {
            getLogger().severe(language.plain(
                    "log.reload-failed",
                    Map.of("error", String.valueOf(exception.getMessage()))
            ));
            return false;
        }
    }

    private void registerPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            Class<?> expansionClass = Class.forName(
                    "dev.che1s.phantomcreeper.stats.PhantomCreeperExpansion");
            Object expansion = expansionClass
                    .getConstructor(JavaPlugin.class, StatsService.class)
                    .newInstance(this, stats);
            boolean registered = (boolean) expansionClass
                    .getMethod("register")
                    .invoke(expansion);
            if (registered) {
                placeholderExpansion = expansion;
            }
        } catch (ReflectiveOperationException exception) {
            getLogger().log(java.util.logging.Level.WARNING,
                    language.plain("log.placeholder-register-failed", Map.of()), exception);
        }
    }

    private void unregisterPlaceholderApi() {
        if (placeholderExpansion == null) {
            return;
        }
        try {
            placeholderExpansion.getClass().getMethod("unregister").invoke(placeholderExpansion);
        } catch (ReflectiveOperationException exception) {
            getLogger().log(java.util.logging.Level.WARNING,
                    language.plain("log.placeholder-unregister-failed", Map.of()), exception);
        } finally {
            placeholderExpansion = null;
        }
    }

    private void syncBstats(boolean enabled) {
        if (!enabled) {
            if (metrics != null) {
                metrics.shutdown();
                metrics = null;
            }
            return;
        }
        if (metrics != null) {
            return;
        }
        try {
            metrics = new Metrics(this, 35223);
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.WARNING,
                    language.plain("log.bstats-init-failed", Map.of()), exception);
        }
    }
}
