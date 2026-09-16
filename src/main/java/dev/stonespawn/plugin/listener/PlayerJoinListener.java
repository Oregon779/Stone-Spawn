package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.SpawnManager;
import dev.stonespawn.plugin.model.JoinMode;
import dev.stonespawn.plugin.model.TeleportContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final StoneSpawn plugin;

    public PlayerJoinListener(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SpawnManager spawnManager = plugin.getSpawnManager();

        boolean joinedBefore = spawnManager.hasJoinedBefore(player.getUniqueId());

        if (spawnManager.hasSpawn()) {
            JoinMode mode = plugin.getConfigManager().getJoinMode();
            boolean shouldTeleport = switch (mode) {
                case ALWAYS -> true;
                case FIRST_JOIN -> !joinedBefore;
                case NEVER -> false;
            };

            if (shouldTeleport) {
                Location spawn = spawnManager.getSpawn();
                if (spawn != null) {
                    TeleportContext context = joinedBefore
                            ? TeleportContext.instantContext()
                            : TeleportContext.instantSilentContext();
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> plugin.getTeleportManager().startTeleport(player, spawn, context), 1L);
                }
            }
        }

        if (!joinedBefore) {
            spawnManager.markJoined(player.getUniqueId());
        }
    }
}
