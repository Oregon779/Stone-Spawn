package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.MessageManager;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DelSpawnCommand implements CommandExecutor, TabCompleter {

    private final StoneSpawn plugin;

    public DelSpawnCommand(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        if (!sender.hasPermission("stonespawn.admin")) {
            mm.sendChat(sender, "general.no-permission", null);
            return true;
        }
        if (args.length == 0) {
            mm.sendChat(sender, "delspawn.usage", null);
            return true;
        }

        String name = SpawnManager.normalize(args[0]);
        if (!plugin.getSpawnManager().deleteSpawn(name)) {
            mm.sendChat(sender, "spawn.not-found", Map.of("spawn", MessageManager.sanitizeInput(args[0])));
            return true;
        }

        plugin.getEffectManager().startSpawnMarker();
        mm.sendChat(sender, "delspawn.success", Map.of("spawn", name));
        return true;
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
