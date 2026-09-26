package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class TeleportMoveListener implements Listener {

    private final StoneSpawn plugin;

    public TeleportMoveListener(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTeleportManager().hasPending(player.getUniqueId())) {
            return;
        }

        if (!plugin.getConfigManager().isCancelOnMove()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            plugin.getTeleportManager().cancelTeleport(player, false);
        }
    }
}
