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
        plugin.getCooldownManager().clear(player.getUniqueId());
        plugin.getTeleportManager().cancelTeleport(player, true);
    }
}
