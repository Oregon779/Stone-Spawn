package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.model.TeleportContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public class WorldChangeListener implements Listener {

    private final StoneSpawn plugin;

    public WorldChangeListener(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!plugin.getConfigManager().getBoolean("spawn.world-change.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.getWorldRestrictionManager().isAllowed(player.getWorld())) {
            return;
        }
        if (plugin.getTeleportManager().isInFlight(player.getUniqueId())) {
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
