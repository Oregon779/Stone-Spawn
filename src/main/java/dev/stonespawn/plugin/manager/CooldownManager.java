package dev.stonespawn.plugin.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Long> lastUse = new HashMap<>();

    public int getRemaining(UUID uuid, int cooldownSeconds) {
        Long last = lastUse.get(uuid);
        if (last == null) {
            return 0;
        }
        long elapsedSeconds = (System.currentTimeMillis() - last) / 1000L;
        long remaining = cooldownSeconds - elapsedSeconds;
        return remaining > 0 ? (int) remaining : 0;
    }

    public void setUsed(UUID uuid) {
        lastUse.put(uuid, System.currentTimeMillis());
    }

    public void clear(UUID uuid) {
        lastUse.remove(uuid);
    }
}
