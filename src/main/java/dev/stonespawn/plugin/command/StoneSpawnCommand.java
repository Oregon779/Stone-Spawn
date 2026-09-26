package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StoneSpawnCommand implements CommandExecutor, TabCompleter {

    private final StoneSpawn plugin;

    public StoneSpawnCommand(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                plugin.reload();
                plugin.getMessageManager().sendChat(sender, "general.reload-success", null);
            }
            case "checkupdate" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                plugin.getUpdateChecker().checkNow();
                plugin.getMessageManager().sendChat(sender, "update.check-triggered", null);
            }
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("stonespawn.admin")) {
            return true;
        }
        plugin.getMessageManager().sendChat(sender, "general.no-permission", null);
        return false;
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        boolean isAdmin = sender.hasPermission("stonespawn.admin");

        mm.sendRaw(sender, "help.header", null);
        if (isAdmin) {
            mm.sendRaw(sender, "help.setspawn", null);
            mm.sendRaw(sender, "help.setspawn-named", null);
            mm.sendRaw(sender, "help.delspawn", null);
        }
        if (sender.hasPermission("stonespawn.use")) {
            mm.sendRaw(sender, "help.spawn", null);
            mm.sendRaw(sender, "help.spawn-named", null);
        }
        if (isAdmin) {
            mm.sendRaw(sender, "help.reload", null);
            mm.sendRaw(sender, "help.checkupdate", null);
        }
        mm.sendRaw(sender, "help.help", null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            boolean isAdmin = sender.hasPermission("stonespawn.admin");
            Stream<String> options = isAdmin ? Stream.of("reload", "help", "checkupdate") : Stream.of("help");
            return options.filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
