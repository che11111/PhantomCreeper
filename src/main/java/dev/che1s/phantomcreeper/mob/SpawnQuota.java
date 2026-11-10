package dev.che1s.phantomcreeper.mob;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 以原子方式预留和释放生成名额，包含正在生成的实体。
 */
final class SpawnQuota {
    private final AtomicInteger tracked = new AtomicInteger();
    private final AtomicInteger natural = new AtomicInteger();

    boolean reserve(
            PhantomCreeperManager.SpawnSource source,
            int maxTracked,
            int maxNatural) {
        if (!incrementBelow(tracked, maxTracked)) {
            return false;
        }
        if (source != PhantomCreeperManager.SpawnSource.NATURAL
                || incrementBelow(natural, maxNatural)) {
            return true;
        }
        tracked.updateAndGet(value -> Math.max(0, value - 1));
        return false;
    }

    void release(PhantomCreeperManager.SpawnSource source) {
        tracked.updateAndGet(value -> Math.max(0, value - 1));
        if (source == PhantomCreeperManager.SpawnSource.NATURAL) {
            natural.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    int naturalReservations() {
        return natural.get();
    }

    void reset() {
        tracked.set(0);
        natural.set(0);
    }

    private static boolean incrementBelow(AtomicInteger counter, int limit) {
        while (true) {
            int current = counter.get();
            if (current >= limit) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
