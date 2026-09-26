package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.MessageManager;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SetSpawnCommand implements CommandExecutor, TabCompleter {

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

        if (args.length == 0) {
            plugin.getSpawnManager().setSpawn(centerOnBlock(player.getLocation()));
            plugin.getEffectManager().startSpawnMarker();
            plugin.getMessageManager().sendChat(player, "setspawn.success", null);
            return true;
        }

        if (!SpawnManager.isValidName(args[0])) {
            plugin.getMessageManager().sendChat(player, "setspawn.invalid-name", Map.of("spawn", MessageManager.sanitizeInput(args[0])));
            return true;
        }

        String name = SpawnManager.normalize(args[0]);
        plugin.getSpawnManager().setSpawn(name, centerOnBlock(player.getLocation()));
        plugin.getEffectManager().startSpawnMarker();
        plugin.getMessageManager().sendChat(player, "setspawn.success-named", Map.of("spawn", name));
        return true;
    }

    private Location centerOnBlock(Location location) {
        double x = Math.floor(location.getX()) + 0.5;
        double z = Math.floor(location.getZ()) + 0.5;
        return new Location(location.getWorld(), x, location.getY(), z, location.getYaw(), location.getPitch());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("stonespawn.admin")) {
            String partial = args[0].toLowerCase();
            return plugin.getSpawnManager().getSpawnNames().stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
