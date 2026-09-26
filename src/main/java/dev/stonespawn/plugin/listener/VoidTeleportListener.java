package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.ConfigManager;
import dev.stonespawn.plugin.model.TeleportContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class VoidTeleportListener implements Listener {

    private final StoneSpawn plugin;

    public VoidTeleportListener(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isVoidTeleportEnabled()) {
            return;
        }
        if (to.getY() >= cfg.getVoidHeight()) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.getWorldRestrictionManager().isAllowed(player.getWorld())) {
            return;
        }
        if (plugin.getTeleportManager().hasPending(player.getUniqueId())) {
            return;
        }
        if (!plugin.getSpawnManager().hasSpawn()) {
            return;
        }
        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn == null) {
            return;
        }

        plugin.getTeleportManager().startTeleport(player, spawn, TeleportContext.instantContext());
    }
}
