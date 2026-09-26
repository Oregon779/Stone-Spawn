package dev.stonespawn.plugin.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private static final int PRUNE_THRESHOLD = 256;

    private final Map<UUID, Long> lastUse = new HashMap<>();

    public int getRemaining(UUID uuid, int cooldownSeconds) {
        Long last = lastUse.get(uuid);
        if (last == null) {
            return 0;
        }
        return remaining(last, cooldownSeconds);
    }

    public void setUsed(UUID uuid) {
        lastUse.put(uuid, System.currentTimeMillis());
    }

    /** Keeps a running cooldown across reconnects, so it can't be skipped by relogging. */
    public void handleQuit(UUID uuid, int cooldownSeconds) {
        if (getRemaining(uuid, cooldownSeconds) == 0) {
            lastUse.remove(uuid);
        }
        if (lastUse.size() > PRUNE_THRESHOLD) {
            lastUse.values().removeIf(last -> remaining(last, cooldownSeconds) == 0);
        }
    }

    int size() {
        return lastUse.size();
    }

    private static int remaining(long last, int cooldownSeconds) {
        long elapsedSeconds = (System.currentTimeMillis() - last) / 1000L;
        long remaining = cooldownSeconds - elapsedSeconds;
        return remaining > 0 ? (int) remaining : 0;
    }
}
