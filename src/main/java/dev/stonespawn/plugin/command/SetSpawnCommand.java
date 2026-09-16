package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final StoneSpawn plugin;

    public SetSpawnCommand(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().sendChat(sender, "general.player-only", null);
            return true;
        }

        if (!player.hasPermission("stonespawn.admin")) {
            plugin.getMessageManager().sendChat(player, "general.no-permission", null);
            return true;
        }

        plugin.getSpawnManager().setSpawn(centerOnBlock(player.getLocation()));
        plugin.getEffectManager().startSpawnMarker();
        plugin.getMessageManager().sendChat(player, "setspawn.success", null);
        return true;
    }

    private Location centerOnBlock(Location location) {
        double x = Math.floor(location.getX()) + 0.5;
        double z = Math.floor(location.getZ()) + 0.5;
        return new Location(location.getWorld(), x, location.getY(), z, location.getYaw(), location.getPitch());
    }
}
