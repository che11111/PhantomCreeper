package dev.che1s.phantomcreeper.model;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.lang.Language;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.BetterModelBukkit;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.ModelAsset;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.event.ModelAssetsEvent;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.ModelImportedEvent;
import kr.toxicity.model.api.event.PluginStartReloadEvent;
import kr.toxicity.model.api.tracker.DummyTracker;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerModifier;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * BetterModel 渲染后端。
 */
final class BetterModelBackend implements ModelBackend {
    private final JavaPlugin plugin;
    private final Supplier<PluginSettings> settings;
    private final Language language;
    private final List<ModelEventListener> listeners = new ArrayList<>();
    private final Set<DummyTracker> deathEffects = ConcurrentHashMap.newKeySet();
    private volatile Runnable importedCallback = () -> {
    };

    BetterModelBackend(
            JavaPlugin plugin,
            Supplier<PluginSettings> settings,
            Language language) {
        this.plugin = plugin;
        this.settings = settings;
        this.language = language;
    }

    @Override
    public String name() {
        return "BetterModel";
    }

    @Override
    public void register(Runnable callback) {
        importedCallback = callback;
        listeners.add(BetterModelBukkit.platform().eventBus().subscribe(
                plugin,
                ModelAssetsEvent.class,
                event -> {
                    if (event.type() != ModelRenderer.Type.GENERAL) {
                        return;
                    }
                    String modelName = settings.get().model().name();
                    ModelAsset asset = ModelAsset.of(modelName, () -> {
                        var stream = plugin.getResource(
                                "models/bettermodel/phantom_creeper.bbmodel");
                        if (stream == null) {
                            throw new IllegalStateException(
                                    language.plain("log.model-resource-missing", Map.of()));
                        }
                        return stream;
                    });
                    try {
                        event.addAsset(asset);
                    } catch (IllegalArgumentException ignored) {
                        // 同名模型已经通过其他重载流程注入。
                    }
                }
        ));
        listeners.add(BetterModelBukkit.platform().eventBus().subscribe(
                plugin,
                ModelImportedEvent.class,
                event -> {
                    if (event.renderer().name().equals(settings.get().model().name())) {
                        importedCallback.run();
                    }
                }
        ));
        listeners.add(BetterModelBukkit.platform().eventBus().subscribe(
                plugin,
                PluginStartReloadEvent.class,
                event -> event.zipper().metaBuilder().description(
                        language.plain("resourcepack.description", Map.of()))
        ));
    }

    @Override
    public boolean isLoaded() {
        return BetterModel.modelOrNull(settings.get().model().name()) != null;
    }

    @Override
    public ModelAttachment attach(LivingEntity entity) {
        PluginSettings.Model model = settings.get().model();
        ModelRenderer renderer = BetterModel.modelOrNull(model.name());
        if (renderer == null) {
            return null;
        }
        TrackerModifier modifier = TrackerModifier.builder()
                .sightTrace(true)
                .damageAnimation(true)
                .damageTint(true)
                .build();
        EntityTracker tracker = renderer.getOrCreate(BukkitAdapter.adapt(entity), modifier);
        tracker.scaler(ModelScaler.value((float) model.scale()));
        tracker.animate(model.flapAnimation(), AnimationModifier.DEFAULT);
        return new Attachment(tracker);
    }

    @Override
    public void playDeathEffect(Location location) {
        PluginSettings.Model model = settings.get().model();
        ModelRenderer renderer = BetterModel.modelOrNull(model.name());
        if (renderer == null) {
            return;
        }
        DummyTracker dummy = renderer.create(
                BukkitAdapter.adapt(location),
                TrackerModifier.DEFAULT
        );
        dummy.scaler(ModelScaler.value((float) model.scale()));
        deathEffects.add(dummy);
        boolean hasViewer = false;
        for (Player player : location.getWorld().getNearbyPlayers(location, 64.0)) {
            dummy.spawn(BukkitAdapter.adapt(player));
            hasViewer = true;
        }
        Runnable close = () -> {
            deathEffects.remove(dummy);
            if (!dummy.isClosed()) {
                dummy.close();
            }
        };
        if (!hasViewer
                || !dummy.animate("death", AnimationModifier.DEFAULT_WITH_PLAY_ONCE, close)) {
            close.run();
        }
    }

    @Override
    public void close() {
        listeners.forEach(ModelEventListener::unregister);
        listeners.clear();
        deathEffects.forEach(DummyTracker::close);
        deathEffects.clear();
    }

    private static final class Attachment implements ModelAttachment {
        private final EntityTracker tracker;

        private Attachment(EntityTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public int hitboxCount() {
            return tracker.registry().hitBoxes().size();
        }

        @Override
        public void damageTint() {
            tracker.damageTint();
        }

        @Override
        public void setHeadRotation(float yaw, float pitch) {
            // BetterModel 会从原版载体的头部旋转同步俯仰角与偏航角。
        }

        @Override
        public void setPatrolHeadLocked(boolean locked) {
            // BetterModel 的实体旋转器已经能正确处理盘旋时的头部角度。
        }

        @Override
        public void updateLocation(Location location) {
            // BetterModel 追踪器会直接读取原版载体的位置。
        }

        @Override
        public boolean isValid() {
            return !tracker.isClosed();
        }

        @Override
        public void close() {
            if (!tracker.isClosed()) {
                tracker.close();
            }
        }
    }
}
