package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitCleanupListener implements Listener {

    private final StoneSpawn plugin;

    public PlayerQuitCleanupListener(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getCooldownManager().handleQuit(player.getUniqueId(),
                plugin.getConfigManager().getInt("command.cooldown-seconds", 10));
        plugin.getTeleportManager().cancelTeleport(player, true);
        plugin.getTeleportManager().clear(player.getUniqueId());
        plugin.getNotificationManager().clear(player);
    }
}
