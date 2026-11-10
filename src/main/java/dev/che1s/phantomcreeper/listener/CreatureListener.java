package dev.che1s.phantomcreeper.listener;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.mob.PhantomCreeperController;
import dev.che1s.phantomcreeper.mob.PhantomCreeperManager;
import dev.che1s.phantomcreeper.stats.StatsService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public final class CreatureListener implements Listener {
    private final PhantomCreeperManager manager;
    private final Supplier<PluginSettings> settings;
    private final StatsService stats;

    public CreatureListener(
            PhantomCreeperManager manager,
            Supplier<PluginSettings> settings,
            StatsService stats) {
        this.manager = manager;
        this.settings = settings;
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        PhantomCreeperController controller = manager.controller(event.getEntity());
        if (controller != null) {
            controller.damageTint();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        manager.adjustExplosionDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        PhantomCreeperController controller = manager.controller(event.getEntity());
        if (controller == null) {
            return;
        }
        PluginSettings.Combat combat = settings.get().combat();
        if (!controller.onDeath()) {
            return;
        }
        if (event.getEntity().getKiller() != null) {
            stats.incrementKills(event.getEntity().getKiller());
        }
        if (combat.phantomMembraneDrops() > 0) {
            event.getDrops().add(new ItemStack(Material.PHANTOM_MEMBRANE, combat.phantomMembraneDrops()));
        }
        if (combat.gunpowderDrops() > 0) {
            event.getDrops().add(new ItemStack(Material.GUNPOWDER, combat.gunpowderDrops()));
        }
        event.setDroppedExp(Math.max(event.getDroppedExp(), combat.experience()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (manager.consumeExplosionDeath(event.getEntity().getUniqueId())) {
            stats.incrementDeaths(event.getEntity());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (manager.isManaged(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (manager.isManaged(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!settings.get().safety().removeOnChunkUnload()) {
            return;
        }
        for (var entity : event.getChunk().getEntities()) {
            PhantomCreeperController controller = manager.controller(entity);
            if (controller != null) {
                controller.removeWithModel();
            }
        }
    }
}
