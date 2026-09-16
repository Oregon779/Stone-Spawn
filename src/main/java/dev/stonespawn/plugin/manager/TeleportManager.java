package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.model.PendingTeleport;
import dev.stonespawn.plugin.model.TeleportContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager {

    private final StoneSpawn plugin;
    private final Map<UUID, PendingTeleport> pending = new HashMap<>();
    private final Set<UUID> fallDamageImmune = new HashSet<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public TeleportManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public PendingTeleport getPending(UUID uuid) {
        return pending.get(uuid);
    }

    public void startTeleport(Player player, Location destination, TeleportContext context) {
        ConfigManager cfg = plugin.getConfigManager();
        int delay = cfg.getInt("teleport.delay-seconds", 3);
        boolean bypassDelay = player.hasPermission("stonespawn.bypass.delay");

        if (context.instant() || delay <= 0 || bypassDelay) {
            executeTeleport(player, destination, context);
            return;
        }

        if (pending.containsKey(player.getUniqueId())) {
            return;
        }

        boolean blindness = cfg.getBoolean("teleport.blindness-during-delay", false);
        if (blindness) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (delay + 1) * 20, 1, false, false, false));
        }

        PendingTeleport pendingTeleport = new PendingTeleport(player.getUniqueId(), player.getLocation(), destination, context);
        pending.put(player.getUniqueId(), pendingTeleport);

        plugin.getEffectManager().startCountdownEffects(player, delay);
        plugin.getNotificationManager().sendCountdown(player, delay);
        plugin.getEffectManager().playCountdownTick(player, delay, delay);

        BukkitTask task = new BukkitRunnable() {
            int remaining = delay;

            @Override
            public void run() {
                remaining--;

                if (!pending.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                if (!player.isOnline()) {
                    pending.remove(player.getUniqueId());
                    plugin.getEffectManager().stopCountdownEffects(player.getUniqueId());
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    pending.remove(player.getUniqueId());
                    plugin.getEffectManager().stopCountdownEffects(player.getUniqueId());
                    executeTeleport(player, destination, context);
                    cancel();
                    return;
                }

                plugin.getNotificationManager().sendCountdown(player, remaining);
                plugin.getEffectManager().playCountdownTick(player, remaining, delay);
            }
        }.runTaskTimer(plugin, 20L, 20L);

        pendingTeleport.setTask(task);
    }

    public void cancelTeleport(Player player, boolean silent) {
        PendingTeleport pendingTeleport = pending.remove(player.getUniqueId());
        if (pendingTeleport == null) {
            return;
        }
        if (pendingTeleport.getTask() != null) {
            pendingTeleport.getTask().cancel();
        }
        plugin.getEffectManager().stopCountdownEffects(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (!silent) {
            plugin.getMessageManager().sendChat(player, "spawn.cancelled-move", null);
        }
    }

    private void executeTeleport(Player player, Location destination, TeleportContext context) {
        if (!inFlight.add(player.getUniqueId())) {
            return;
        }

        ConfigManager cfg = plugin.getConfigManager();

        if (cfg.getBoolean("teleport.disable-fall-damage", true)) {
            fallDamageImmune.add(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> fallDamageImmune.remove(player.getUniqueId()), 100L);
        }

        player.teleportAsync(destination).whenComplete((success, throwable) -> {
            inFlight.remove(player.getUniqueId());
            if (throwable != null || !Boolean.TRUE.equals(success)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                if (!context.silent()) {
                    if (cfg.getBoolean("teleport.arrival.effects-enabled", true)) {
                        plugin.getEffectManager().playArrivalEffect(player);
                    }
                    if (cfg.getBoolean("teleport.arrival.message-enabled", true)) {
                        plugin.getNotificationManager().sendArrival(player);
                    }
                }
                context.onComplete(player);
            });
        });
    }

    public boolean isFallDamageImmune(UUID uuid) {
        return fallDamageImmune.contains(uuid);
    }

    public boolean isInFlight(UUID uuid) {
        return inFlight.contains(uuid);
    }
}
