package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerDeathRespawnListener implements Listener {

    private final StoneSpawn plugin;

    public PlayerDeathRespawnListener(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfigManager().getBoolean("spawn.death.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();

        boolean ignoreBed = plugin.getConfigManager().getBoolean("spawn.death.ignore-bed-spawn", true);
        boolean ignoreAnchor = plugin.getConfigManager().getBoolean("spawn.death.ignore-respawn-anchor", true);

        if (event.isBedSpawn() && ignoreBed) {
            return;
        }
        if (event.isAnchorSpawn() && ignoreAnchor) {
            return;
        }

        if (!plugin.getSpawnManager().hasSpawn()) {
            return;
        }
        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn == null) {
            return;
        }
        if (!plugin.getWorldRestrictionManager().isAllowed(spawn.getWorld())) {
            return;
        }

        event.setRespawnLocation(spawn);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getEffectManager().playArrivalEffect(player);
                plugin.getNotificationManager().sendArrival(player);
            }
        }, 2L);
    }
}
