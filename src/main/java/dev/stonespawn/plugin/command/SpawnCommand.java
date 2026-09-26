package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.MessageManager;
import dev.stonespawn.plugin.manager.SpawnManager;
import dev.stonespawn.plugin.model.TeleportContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final StoneSpawn plugin;

    public SpawnCommand(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        SpawnManager spawnManager = plugin.getSpawnManager();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                mm.sendChat(sender, "general.player-only", null);
                return true;
            }
            if (!player.hasPermission("stonespawn.use")) {
                mm.sendChat(player, "general.no-permission", null);
                return true;
            }
            if (spawnManager.getSpawnCount() > 1 && plugin.getConfigManager().getBoolean("command.spawn-gui", true)) {
                plugin.getSpawnGui().open(player);
                return true;
            }
            teleportSelf(player, spawnManager.getSpawn(), "spawn.no-spawn-set", null);
            return true;
        }

        String first = args[0];
        if (spawnManager.hasSpawn(first)) {
            String spawnName = SpawnManager.normalize(first);
            if (args.length >= 2) {
                teleportOther(sender, args[1], spawnManager.getSpawn(spawnName));
                return true;
            }
            if (!(sender instanceof Player player)) {
                mm.sendChat(sender, "general.player-only", null);
                return true;
            }
            if (!player.hasPermission("stonespawn.use")) {
                mm.sendChat(player, "general.no-permission", null);
                return true;
            }
            teleportSelf(player, spawnManager.getSpawn(spawnName), "spawn.not-found", Map.of("spawn", first));
            return true;
        }

        if (args.length == 1 && sender.hasPermission("stonespawn.admin") && Bukkit.getPlayerExact(first) != null) {
            teleportOther(sender, first, spawnManager.getSpawn());
            return true;
        }

        mm.sendChat(sender, "spawn.not-found", Map.of("spawn", first));
        return true;
    }

    private void teleportSelf(Player player, Location spawn, String missingKey, Map<String, String> placeholders) {
        if (spawn == null) {
            plugin.getMessageManager().sendChat(player, missingKey, placeholders);
            return;
        }
        plugin.getTeleportManager().requestCommandTeleport(player, spawn);
    }

    private void teleportOther(CommandSender sender, String targetName, Location spawn) {
        MessageManager mm = plugin.getMessageManager();
        if (!sender.hasPermission("stonespawn.admin")) {
            mm.sendChat(sender, "general.no-permission", null);
            return;
        }
        if (spawn == null) {
            mm.sendChat(sender, "spawn.no-spawn-set", null);
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            mm.sendChat(sender, "general.player-not-found", Map.of("player", targetName));
            return;
        }

        if (!plugin.getWorldRestrictionManager().isAllowed(target.getWorld())) {
            mm.sendChat(sender, "general.world-blocked", null);
            return;
        }

        String initiatorName = sender.getName();
        plugin.getTeleportManager().startTeleport(target, spawn,
                new TeleportContext(false, false, p -> mm.sendChat(p, "spawn.teleported-by", Map.of("player", initiatorName))));
        mm.sendChat(sender, "spawn.teleported-other", Map.of("player", target.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("stonespawn.admin");
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("stonespawn.use") || isAdmin) {
                options.addAll(plugin.getSpawnManager().getSpawnNames());
            }
            if (isAdmin) {
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && isAdmin && plugin.getSpawnManager().hasSpawn(args[0])) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lower))
                .distinct()
                .collect(Collectors.toList());
    }
}
