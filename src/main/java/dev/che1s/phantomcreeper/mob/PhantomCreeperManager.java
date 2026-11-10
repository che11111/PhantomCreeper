package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.lang.Language;
import dev.che1s.phantomcreeper.model.PhantomModel;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import dev.che1s.phantomcreeper.util.ExplosionMath;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PhantomCreeperManager {
    public enum SpawnSource {
        NATURAL,
        COMMAND
    }

    private final JavaPlugin plugin;
    private final Supplier<PluginSettings> settings;
    private final Language language;
    private final PhantomModel model;
    private final NamespacedKey markerKey;
    private final Map<UUID, PhantomCreeperController> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentExplosionVictims = new ConcurrentHashMap<>();
    private final SpawnQuota spawnQuota = new SpawnQuota();
    private final ThreadLocal<ExplosionContext> explosionContext = new ThreadLocal<>();
    private final PlatformScheduler.TaskHandle explosionCleanupTask;

    public PhantomCreeperManager(
            JavaPlugin plugin,
            Supplier<PluginSettings> settings,
            Language language,
            PhantomModel model) {
        this.plugin = plugin;
        this.settings = settings;
        this.language = language;
        this.model = model;
        this.markerKey = new NamespacedKey(plugin, "phantom_creeper");
        this.explosionCleanupTask = PlatformScheduler.globalTimer(
                plugin,
                20L,
                20L,
                this::purgeExpiredExplosionVictims
        );
    }

    @SuppressWarnings("deprecation")
    public PhantomCreeperController spawnNow(Location location, UUID preferredTarget) {
        return spawnNow(location, preferredTarget, SpawnSource.COMMAND);
    }

    @SuppressWarnings("deprecation")
    public PhantomCreeperController spawnNow(
            Location location,
            UUID preferredTarget,
            SpawnSource source) {
        World world = location.getWorld();
        if (world == null
                || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return null;
        }
        PluginSettings current = settings.get();
        if (!spawnQuota.reserve(
                source,
                Math.max(1, current.naturalSpawn().maxTracked()),
                current.naturalSpawn().maxActive()
        )) {
            return null;
        }
        Creeper carrier = null;
        try {
            carrier = world.spawn(location, Creeper.class, entity -> {
                // 控制器在载体所属的区域线程中更新位置；关闭原版寻路，
                // 避免它覆盖插件计算出的飞行轨迹。
                entity.setAI(false);
                entity.setGravity(false);
                entity.setCollidable(false);
                entity.setPersistent(false);
                entity.setRemoveWhenFarAway(false);
                entity.setCanPickupItems(false);
                entity.setSilent(false);
                entity.setFireTicks(0);
                entity.setMaxHealth(current.combat().maxHealth());
                entity.setHealth(current.combat().maxHealth());
                entity.customName(language.component("creature.name", Map.of()));
                entity.setCustomNameVisible(false);
                entity.getPersistentDataContainer().set(
                        markerKey, PersistentDataType.BYTE, (byte) 1);
                entity.addScoreboardTag("phantomcreeper");
                entity.setTarget(null);
                entity.setIgnited(false);
                if (!current.model().fallbackVisible() && !model.isLoaded()) {
                    entity.setInvisible(true);
                }
            });
        } catch (RuntimeException exception) {
            spawnQuota.release(source);
            plugin.getLogger().warning(language.plain(
                    "log.spawn-failed",
                    Map.of("error", String.valueOf(exception.getMessage()))
            ));
            return null;
        }
        try {
            PhantomCreeperController controller = new PhantomCreeperController(
                    plugin, this, carrier, settings, model, preferredTarget, source);
            active.put(carrier.getUniqueId(), controller);
            controller.start();
            if (model.isLoaded()) {
                controller.attachModel();
            }
            return controller;
        } catch (RuntimeException exception) {
            PhantomCreeperController controller = active.get(carrier.getUniqueId());
            if (controller != null) {
                controller.removeWithModel();
            } else {
                carrier.remove();
                spawnQuota.release(source);
            }
            plugin.getLogger().warning(language.plain(
                    "log.spawn-failed",
                    Map.of("error", String.valueOf(exception.getMessage()))
            ));
            return null;
        }
    }

    public PhantomCreeperController controller(Entity entity) {
        if (entity == null) {
            return null;
        }
        return active.get(entity.getUniqueId());
    }

    public boolean isManaged(Entity entity) {
        return entity != null
                && (active.containsKey(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE));
    }

    public int size() {
        return active.size();
    }

    public int naturalCount() {
        return Math.min(spawnQuota.naturalReservations(), active.size());
    }

    public int naturalLimit() {
        return settings.get().naturalSpawn().maxActive();
    }

    public int countNear(UUID playerId) {
        int count = 0;
        for (PhantomCreeperController controller : active.values()) {
            if (playerId.equals(controller.primaryTarget())) {
                count++;
            }
        }
        return count;
    }

    public Collection<PhantomCreeperController> activeControllers() {
        return active.values();
    }

    public PhantomCreeperController firstController() {
        return active.values().stream().findFirst().orElse(null);
    }

    public Vector separationVector(PhantomCreeperController source, double radius) {
        Vector result = new Vector();
        Location sourceLocation = source.cachedLocation();
        double radiusSquared = radius * radius;
        for (PhantomCreeperController other : active.values()) {
            if (other == source || !other.sharesWorld(source)) {
                continue;
            }
            Location otherLocation = other.cachedLocation();
            Vector away = sourceLocation.toVector().subtract(otherLocation.toVector());
            double distanceSquared = away.lengthSquared();
            if (distanceSquared >= radiusSquared) {
                continue;
            }
            if (distanceSquared < 1.0E-6) {
                double angle = source.entity().getUniqueId().hashCode() * 0.017453292519943295;
                away = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
            } else {
                away.normalize();
            }
            double distance = Math.sqrt(Math.max(distanceSquared, 0.0));
            result.add(away.multiply((radius - distance) / radius));
        }
        return result;
    }

    public void withExplosionContext(
            PluginSettings.Explosion settings,
            Location center,
            Runnable explosionAction) {
        ExplosionContext previous = explosionContext.get();
        explosionContext.set(new ExplosionContext(settings, center.clone()));
        try {
            explosionAction.run();
        } finally {
            if (previous == null) {
                explosionContext.remove();
            } else {
                explosionContext.set(previous);
            }
        }
    }

    public void adjustExplosionDamage(EntityDamageEvent event) {
        ExplosionContext context = explosionContext.get();
        if (context == null
                || (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            return;
        }
        Location entityLocation = event.getEntity().getLocation();
        if (!entityLocation.getWorld().equals(context.center().getWorld())) {
            return;
        }
        double distance = entityLocation.distance(context.center());
        event.setDamage(ExplosionMath.damageAtDistance(
                context.settings().damage(),
                context.settings().radius(),
                context.settings().damageFalloff(),
                distance
        ));
        if (event.getEntity() instanceof Player player) {
            long now = System.currentTimeMillis();
            purgeExpiredExplosionVictims(now);
            recentExplosionVictims.put(player.getUniqueId(), now + 500L);
        }
    }

    public boolean consumeExplosionDeath(UUID playerId) {
        Long expiry = recentExplosionVictims.remove(playerId);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    public void forget(PhantomCreeperController controller) {
        if (active.remove(controller.entity().getUniqueId(), controller)) {
            spawnQuota.release(controller.spawnSource());
        }
    }

    public void onModelImported() {
        plugin.getLogger().info(language.plain("model.loaded", Map.of(
                "engine", model.backendName(),
                "model", settings.get().model().name()
        )));
        for (PhantomCreeperController controller : active.values()) {
            PlatformScheduler.runOnEntity(
                    plugin,
                    controller.entity(),
                    controller::attachModel
            );
        }
    }

    public void removeAll() {
        for (PhantomCreeperController controller : active.values()) {
            PlatformScheduler.runOnEntity(plugin, controller.entity(), controller::removeWithModel);
        }
    }

    public void reattachAll() {
        for (PhantomCreeperController controller : active.values()) {
            PlatformScheduler.runOnEntity(plugin, controller.entity(), controller::reattachModel);
        }
    }

    public void shutdown() {
        explosionCleanupTask.cancel();
        for (PhantomCreeperController controller : active.values()) {
            controller.closeModel();
        }
        active.clear();
        spawnQuota.reset();
    }

    private void purgeExpiredExplosionVictims() {
        purgeExpiredExplosionVictims(System.currentTimeMillis());
    }

    private void purgeExpiredExplosionVictims(long now) {
        recentExplosionVictims.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private record ExplosionContext(PluginSettings.Explosion settings, Location center) {
    }
}
