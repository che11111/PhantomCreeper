package dev.che1s.phantomcreeper.mob;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 控制幻翼飞行环境音和苦力怕引信音效。
 */
final class CreatureSoundController {
    private int ageTicks;
    private int nextAmbientSound;
    private int nextFlapSound;

    void tickFlight(World world, Location location) {
        ageTicks++;
        if (ageTicks >= nextAmbientSound) {
            world.playSound(
                    location,
                    Sound.ENTITY_PHANTOM_AMBIENT,
                    1.0F,
                    ThreadLocalRandom.current().nextFloat(0.75F, 1.05F)
            );
            nextAmbientSound = ageTicks + ThreadLocalRandom.current().nextInt(70, 121);
        }
        if (ageTicks >= nextFlapSound) {
            world.playSound(
                    location,
                    Sound.ENTITY_PHANTOM_FLAP,
                    1.15F,
                    ThreadLocalRandom.current().nextFloat(0.8F, 1.1F)
            );
            nextFlapSound = ageTicks + ThreadLocalRandom.current().nextInt(34, 61);
        }
    }

    void playPrimed(World world, Location location) {
        world.playSound(location, Sound.ENTITY_CREEPER_PRIMED, 1.0F, 0.5F);
    }
}
