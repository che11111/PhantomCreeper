package dev.che1s.phantomcreeper.model;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.events.ModelRegistrationEvent;
import com.ticxo.modelengine.api.generator.ModelGenerator;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.lang.Language;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * ModelEngine 4.x 渲染后端。
 */
final class ModelEngineBackend implements ModelBackend, Listener {
    private static final String MODEL_RESOURCE =
            "models/modelengine/phantom_creeper.bbmodel";

    private final JavaPlugin plugin;
    private final Supplier<PluginSettings> settings;
    private final Language language;
    private final AtomicBoolean retriedImport = new AtomicBoolean();
    private final AtomicBoolean reportedLoaded = new AtomicBoolean();
    private final AtomicBoolean blueprintChanged = new AtomicBoolean();
    private final AtomicBoolean attachWarningLogged = new AtomicBoolean();
    private volatile boolean blueprintInstalled;
    private volatile Runnable importedCallback = () -> {
    };

    ModelEngineBackend(
            JavaPlugin plugin,
            Supplier<PluginSettings> settings,
            Language language) {
        this.plugin = plugin;
        this.settings = settings;
        this.language = language;
    }

    @Override
    public String name() {
        return "ModelEngine";
    }

    @Override
    public void register(Runnable callback) {
        importedCallback = callback;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (installBlueprintIfNeeded()) {
            blueprintChanged.set(true);
            // 覆盖迟注册模型引擎已完成初次扫描、但尚未重新加载新蓝图的情况。
            PlatformScheduler.laterGlobal(plugin, 100L, this::refreshBlueprintIfNeeded);
        }
    }

    @Override
    public boolean isLoaded() {
        return ModelEngineAPI.getBlueprint(settings.get().model().name()) != null;
    }

    @Override
    public ModelAttachment attach(LivingEntity entity) {
        try {
            ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(settings.get().model().name());
            if (blueprint == null) {
                return null;
            }
            PluginSettings.Model configured = settings.get().model();
            // 始终使用独立载体，避免原版实体运动与模型位置插值互相叠加。
            // Dummy 的命中箱伤害会由 CarrierDummy 转发给真实苦力怕载体。
            Dummy<LivingEntity> dummy = new ModelEngineAttachment.CarrierDummy(entity);
            dummy.setDetectingPlayers(true);
            dummy.setVisible(false);
            ModeledEntity modeled = ModelEngineAPI.createModeledEntity(dummy);
            // ModelEngine 的距离 LOD 会降低远距离位置同步频率，表现为飞行几秒后
            // 周期性左右卡顿。幻翼苦力怕数量有限，这里仅对本模型的移动关闭 LOD。
            modeled.getAnimationLodHandler().setEnabled(false);
            ActiveModel active = modeled.getModel(blueprint.getName()).orElse(null);
            if (active == null) {
                active = ModelEngineAPI.createActiveModel(blueprint);
                active.setScale(configured.scale());
                active.setHitboxScale(configured.scale());
                Optional<ActiveModel> added = modeled.addModel(active, true);
                if (added.isEmpty()) {
                    try {
                        ModelEngineReflection.addModelWithoutEvent(modeled, active);
                    } catch (ReflectiveOperationException | RuntimeException exception) {
                        active.destroy();
                        logAttachFailure(String.valueOf(exception.getMessage()));
                        return null;
                    }
                } else {
                    active = added.get();
                }
            }
            modeled.setSaved(false);
            // 模型成功附加后必须隐藏原版载体；fallbackVisible 仅用于模型不可用时的兜底。
            modeled.setBaseEntityVisible(false);
            modeled.setModelRotationLocked(false);
            active.setScale(configured.scale());
            active.setHitboxScale(configured.scale());
            active.setHitboxVisible(true);
            active.setModelRotationLocked(false);
            ModelEngineReflection.startAnimation(active, configured.flapAnimation(), true);
            return new ModelEngineAttachment(modeled, active, dummy);
        } catch (RuntimeException exception) {
            logAttachFailure(String.valueOf(exception.getMessage()));
            return null;
        }
    }

    @Override
    public void playDeathEffect(Location location) {
        ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(settings.get().model().name());
        if (blueprint == null) {
            return;
        }
        Dummy<Object> dummy = new Dummy<>();
        dummy.syncLocation(location.clone());
        dummy.setVisible(false);
        ModeledEntity modeled = ModelEngineAPI.createModeledEntity(dummy);
        ActiveModel active = ModelEngineAPI.createActiveModel(blueprint);
        active.setScale(settings.get().model().scale());
        active.setHitboxScale(settings.get().model().scale());
        Optional<ActiveModel> added = modeled.addModel(active, true);
        if (added.isEmpty()) {
            try {
                        ModelEngineReflection.addModelWithoutEvent(modeled, active);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                ModelEngineAttachment.destroy(modeled, active);
                plugin.getLogger().warning(language.plain(
                        "log.modelengine-death-effect-failed",
                        Map.of("error", String.valueOf(exception.getMessage()))
                ));
                return;
            }
        } else {
            active = added.get();
        }
        ActiveModel deathModel = active;
        modeled.setSaved(false);
        modeled.setBaseEntityVisible(false);
        deathModel.setHitboxVisible(false);
        deathModel.setModelRotationLocked(true);
        ModelEngineReflection.startAnimation(deathModel, "death", false);
        long delayTicks = deathDelayTicks(blueprint);
        PlatformScheduler.laterAtLocation(plugin, location, delayTicks, () -> {
            ModelEngineAttachment.destroy(modeled, deathModel);
        });
    }

    @EventHandler
    public void onModelRegistration(ModelRegistrationEvent event) {
        if (event.getPhase() != ModelGenerator.Phase.FINISHED) {
            return;
        }
        if (isLoaded() && !blueprintChanged.get()) {
            if (reportedLoaded.compareAndSet(false, true)) {
                importedCallback.run();
            }
            return;
        }
        if (blueprintInstalled && retriedImport.compareAndSet(false, true)) {
            PlatformScheduler.laterGlobal(plugin, 1L, () -> {
                if (blueprintChanged.getAndSet(false) || !isLoaded()) {
                    ModelEngineAPI.getAPI().getModelGenerator().importModels(false);
                }
            });
            return;
        }
        if (retriedImport.get()) {
            plugin.getLogger().warning(language.plain(
                    "log.modelengine-import-incomplete",
                    Map.of("name", settings.get().model().name())
            ));
        }
    }

    @Override
    public void close() {
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    private boolean installBlueprintIfNeeded() {
        if (!settings.get().model().modelEngineAutoInstall()) {
            return false;
        }
        Plugin modelEngine = Bukkit.getPluginManager().getPlugin("ModelEngine");
        if (modelEngine == null) {
            return false;
        }
        String modelName = settings.get().model().name();
        if (!modelName.matches("[A-Za-z0-9._-]+")) {
            plugin.getLogger().warning(language.plain(
                    "log.modelengine-invalid-model-name",
                    Map.of("name", modelName)
            ));
            return false;
        }
        Path blueprintDirectory = modelEngine.getDataFolder().toPath().resolve("blueprints");
        Path target = blueprintDirectory.resolve(modelName + ".bbmodel");
        try {
            Files.createDirectories(blueprintDirectory);
            byte[] bundled = readBundledModel();
            boolean changed = !Files.exists(target)
                    || !sha256(Files.readAllBytes(target)).equals(sha256(bundled));
            if (changed) {
                Path temporary = Files.createTempFile(blueprintDirectory, modelName + "-", ".tmp");
                Files.write(temporary, bundled);
                try {
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                    );
                } finally {
                    Files.deleteIfExists(temporary);
                }
                plugin.getLogger().info(language.plain(
                        "log.modelengine-blueprint-installed",
                        Map.of("path", target.toString())
                ));
            }
            blueprintInstalled = true;
            return changed;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning(language.plain(
                    "log.modelengine-blueprint-install-failed",
                    Map.of("error", String.valueOf(exception.getMessage()))
            ));
            return false;
        }
    }

    private void refreshBlueprintIfNeeded() {
        if (!retriedImport.compareAndSet(false, true)) {
            return;
        }
        if (blueprintChanged.getAndSet(false) || !isLoaded()) {
            ModelEngineAPI.getAPI().getModelGenerator().importModels(false);
        }
    }

    private void logAttachFailure(String detail) {
        if (!attachWarningLogged.compareAndSet(false, true)) {
            return;
        }
        plugin.getLogger().warning(language.plain(
                "log.modelengine-attach-failed",
                Map.of("error", detail)
        ));
    }

    private byte[] readBundledModel() throws IOException {
        try (InputStream stream = plugin.getResource(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IOException(language.plain("log.model-resource-missing", Map.of()));
            }
            return stream.readAllBytes();
        }
    }

    private long deathDelayTicks(ModelBlueprint blueprint) {
        BlueprintAnimation animation = blueprint.getAnimationOrRef("death");
        double seconds = animation == null ? 1.0 : animation.getLength();
        return Math.max(2L, (long) Math.ceil(seconds * 20.0) + 2L);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
