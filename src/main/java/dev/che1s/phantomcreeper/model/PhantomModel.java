package dev.che1s.phantomcreeper.model;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.lang.Language;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 幻翼苦力怕模型门面。
 *
 * <p>所有已安装的模型引擎都会预先注册资源事件，运行时只切换实际渲染后端。</p>
 */
public final class PhantomModel {
    private final Map<String, ModelBackend> backends = new LinkedHashMap<>();
    private volatile ModelBackend backend;
    private volatile Runnable importedCallback = () -> {
    };

    public PhantomModel(
            JavaPlugin plugin,
            Supplier<PluginSettings> settings,
            Language language) {
        if (plugin.getServer().getPluginManager().getPlugin("BetterModel") != null) {
            backends.put("bettermodel", new BetterModelBackend(plugin, settings, language));
        }
        if (plugin.getServer().getPluginManager().getPlugin("ModelEngine") != null) {
            backends.put("modelengine", new ModelEngineBackend(plugin, settings, language));
        }
        backend = configuredBackend(settings.get().model().engine());
    }

    public void register(Runnable callback) {
        importedCallback = callback;
        backends.forEach((name, candidate) -> candidate.register(() -> {
            if (backend == candidate) {
                importedCallback.run();
            }
        }));
    }

    public boolean hasBackend() {
        return backend != null;
    }

    public String backendName() {
        return backend == null ? "none" : backend.name();
    }

    public Collection<String> availableBackends() {
        return backends.values().stream().map(ModelBackend::name).toList();
    }

    public boolean switchBackend(String engine) {
        String normalized = engine == null ? "" : engine.toLowerCase(Locale.ROOT);
        ModelBackend selected = backends.get(normalized);
        if (selected == null) {
            return false;
        }
        backend = selected;
        return true;
    }

    public boolean isLoaded() {
        return backend != null && backend.isLoaded();
    }

    public ModelAttachment attach(LivingEntity entity) {
        return backend == null ? null : backend.attach(entity);
    }

    public void playDeathEffect(Location location) {
        if (backend != null) {
            backend.playDeathEffect(location);
        }
    }

    public void close() {
        backends.values().forEach(ModelBackend::close);
    }

    private ModelBackend configuredBackend(String engine) {
        String preferred = engine == null ? "auto" : engine.toLowerCase(Locale.ROOT);
        if (preferred.equals("auto")) {
            ModelBackend betterModel = backends.get("bettermodel");
            return betterModel != null ? betterModel : backends.get("modelengine");
        }
        return backends.get(preferred);
    }
}
