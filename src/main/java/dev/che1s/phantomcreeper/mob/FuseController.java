package dev.che1s.phantomcreeper.mob;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 管理引信计时、锁定目标和取消状态。
 */
final class FuseController {
    private UUID targetId;
    private int remainingTicks;

    boolean isActive() {
        return remainingTicks > 0;
    }

    UUID targetId() {
        return targetId;
    }

    void start(Player target, int configuredTicks) {
        targetId = target.getUniqueId();
        remainingTicks = Math.max(1, configuredTicks);
    }

    boolean advance() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        return remainingTicks <= 0;
    }

    void cancel() {
        targetId = null;
        remainingTicks = 0;
    }
}
