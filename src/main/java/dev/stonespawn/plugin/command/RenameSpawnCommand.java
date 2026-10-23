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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class RenameSpawnCommand implements CommandExecutor, TabCompleter {

    private final StoneSpawn plugin;

    public RenameSpawnCommand(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        if (!sender.hasPermission("stonespawn.admin")) {
            mm.sendChat(sender, "general.no-permission", null);
            return true;
        }
        if (args.length < 2) {
            mm.sendChat(sender, "renamespawn.usage", null);
            return true;
        }
        if (!SpawnManager.isValidName(args[1])) {
            mm.sendChat(sender, "setspawn.invalid-name", Map.of("spawn", MessageManager.sanitizeInput(args[1])));
            return true;
        }

        SpawnManager spawnManager = plugin.getSpawnManager();
        String from = SpawnManager.normalize(args[0]);
        String to = SpawnManager.normalize(args[1]);
        switch (spawnManager.renameSpawn(from, to)) {
            case NOT_FOUND -> mm.sendChat(sender, "spawn.not-found", Map.of("spawn", MessageManager.sanitizeInput(args[0])));
            case NAME_TAKEN -> mm.sendChat(sender, "renamespawn.name-taken", Map.of("spawn", to));
            case RENAMED -> {
                plugin.getSpawnGui().renameSpawn(from, to);
                boolean main = to.equals(spawnManager.getMainSpawnName());
                mm.sendChat(sender, main ? "renamespawn.success-main" : "renamespawn.success", Map.of("old", from, "new", to));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("stonespawn.admin")) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return plugin.getSpawnManager().getSpawnNames().stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
