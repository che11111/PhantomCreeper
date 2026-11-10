package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 负责解析和搜索幻翼苦力怕的当前目标。
 */
final class TargetSelector {
    private final Creeper entity;

    TargetSelector(Creeper entity) {
        this.entity = entity;
    }

    Player resolve(UUID playerId, PluginSettings current) {
        if (playerId == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null
                || !player.isOnline()
                || !player.getWorld().equals(entity.getWorld())
                || !valid(player, current)) {
            return null;
        }
        double loseRange = current.targeting().loseRange();
        if (player.getLocation().distanceSquared(entity.getLocation())
                > loseRange * loseRange) {
            return null;
        }
        return player;
    }

    Player find(PluginSettings current) {
        Player best = null;
        double bestDistance = current.targeting().acquireRange()
                * current.targeting().acquireRange();
        for (Player player : entity.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance >= bestDistance) {
                continue;
            }
            // 先做低成本的距离筛选，再执行可能较慢的视线检测。
            if (!valid(player, current)) {
                continue;
            }
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    private boolean valid(Player player, PluginSettings current) {
        if (player.isDead() || player.isInvulnerable()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (current.targeting().ignoreCreative() && mode == GameMode.CREATIVE) {
            return false;
        }
        if (current.targeting().ignoreSpectator() && mode == GameMode.SPECTATOR) {
            return false;
        }
        return !current.targeting().requireLineOfSight() || entity.hasLineOfSight(player);
    }
}
