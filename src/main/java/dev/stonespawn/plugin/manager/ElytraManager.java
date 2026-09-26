package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ElytraManager {

    private static final long LANDING_DAMAGE_GRACE_MILLIS = 2000L;

    private final StoneSpawn plugin;
    private final Set<UUID> onSpawnIsland = new HashSet<>();
    private final Map<UUID, Integer> boostsUsedByGlider = new HashMap<>();
    private final Map<UUID, Long> damageGraceUntil = new HashMap<>();

    public ElytraManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public boolean isGliding(UUID uuid) {
        return boostsUsedByGlider.containsKey(uuid);
    }

    public void handleMove(Player player, Location to) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isElytraEnabled()) {
            return;
        }
        UUID id = player.getUniqueId();

        if (isGliding(id)) {
            if (hasLanded(player)) {
                stop(player);
            }
            return;
        }

        if (hasLanded(player)) {
            if (isOnSpawnIsland(to)) {
                onSpawnIsland.add(id);
            } else {
                onSpawnIsland.remove(id);
            }
            return;
        }

        if (onSpawnIsland.contains(id)
                && player.getFallDistance() >= cfg.getElytraMinFallDistance()
                && canStartGliding(player)) {
            start(player);
        }
    }

    /** @return true if the glide must be kept alive, i.e. the toggle event should be cancelled. */
    public boolean shouldKeepGliding(Player player) {
        UUID id = player.getUniqueId();
        if (!isGliding(id)) {
            return false;
        }
        if (hasLanded(player)) {
            end(id);
            return false;
        }
        // Without a real elytra the server keeps adding up the whole descent as fall distance.
        player.setFallDistance(0f);
        return true;
    }

    public boolean tryBoost(Player player) {
        UUID id = player.getUniqueId();
        Integer used = boostsUsedByGlider.get(id);
        ConfigManager cfg = plugin.getConfigManager();
        if (used == null || !player.isGliding() || !cfg.getBoolean("elytra.boost.enabled", true)) {
            return false;
        }
        int maxUses = cfg.getInt("elytra.boost.uses-per-glide", 1);
        if (maxUses > 0 && used >= maxUses) {
            return false;
        }
        boostsUsedByGlider.put(id, used + 1);

        double strength = cfg.getDouble("elytra.boost.strength", 1.5);
        player.setVelocity(player.getLocation().getDirection().multiply(strength));

        EffectManager effects = plugin.getEffectManager();
        Location location = player.getLocation();
        Sound sound = effects.parseSound(cfg.getString("elytra.boost.sound", "ENTITY_FIREWORK_ROCKET_LAUNCH"),
                Sound.ENTITY_FIREWORK_ROCKET_LAUNCH);
        player.getWorld().playSound(location, sound,
                (float) cfg.getDouble("elytra.boost.sound-volume", 1.0),
                (float) cfg.getDouble("elytra.boost.sound-pitch", 1.0));

        int particleCount = cfg.getInt("elytra.boost.particle-count", 20);
        if (particleCount > 0) {
            Particle particle = effects.parseParticle(cfg.getString("elytra.boost.particle", "FIREWORK"), Particle.FIREWORK);
            player.getWorld().spawnParticle(particle, location, particleCount, 0.3, 0.3, 0.3, 0.05);
        }
        return true;
    }

    public boolean isDamageProtected(UUID uuid) {
        if (!plugin.getConfigManager().getBoolean("elytra.disable-damage", true)) {
            return false;
        }
        if (isGliding(uuid)) {
            return true;
        }
        Long until = damageGraceUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /** Ends a running glide and forgets the spawn-island state, e.g. after a teleport or death. */
    public void reset(Player player) {
        onSpawnIsland.remove(player.getUniqueId());
        stop(player);
    }

    public void clear(UUID uuid) {
        onSpawnIsland.remove(uuid);
        boostsUsedByGlider.remove(uuid);
        damageGraceUntil.remove(uuid);
    }

    private void start(Player player) {
        UUID id = player.getUniqueId();
        onSpawnIsland.remove(id);
        damageGraceUntil.remove(id);
        boostsUsedByGlider.put(id, 0);
        player.setGliding(true);
        sendBoostHint(player);
    }

    private void stop(Player player) {
        if (end(player.getUniqueId()) && player.isGliding()) {
            player.setGliding(false);
        }
    }

    private boolean end(UUID id) {
        if (boostsUsedByGlider.remove(id) == null) {
            return false;
        }
        damageGraceUntil.put(id, System.currentTimeMillis() + LANDING_DAMAGE_GRACE_MILLIS);
        return true;
    }

    private void sendBoostHint(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getBoolean("elytra.boost.enabled", true)) {
            return;
        }
        MessageManager mm = plugin.getMessageManager();
        String hint = mm.getRaw("elytra.boost-hint");
        if (hint.isBlank()) {
            return;
        }
        String key = mm.getRaw(cfg.getBoostTrigger().messageKey());
        player.sendActionBar(mm.format(hint, Map.of("key", key)));
    }

    private boolean canStartGliding(Player player) {
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && !player.isFlying()
                && !player.isGliding()
                && !player.isInsideVehicle()
                && player.hasPermission("stonespawn.elytra")
                && plugin.getWorldRestrictionManager().isAllowed(player.getWorld());
    }

    // Player#isOnGround is client-reported; that is fine for ending a purely cosmetic glide.
    @SuppressWarnings("deprecation")
    private boolean hasLanded(Player player) {
        return player.isOnGround() || player.isInWater() || player.isInLava() || player.isClimbing();
    }

    private boolean isOnSpawnIsland(Location location) {
        double radius = plugin.getConfigManager().getElytraRadius();
        double radiusSquared = radius * radius;
        World world = location.getWorld();
        for (Location spawn : plugin.getSpawnManager().getAllSpawnLocations()) {
            if (spawn.getWorld() == world && spawn.distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }
}
