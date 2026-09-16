package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.MessageManager;
import dev.stonespawn.plugin.model.TeleportContext;
import org.bukkit.Bukkit;
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

public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final StoneSpawn plugin;

    public SpawnCommand(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn == null) {
            mm.sendChat(sender, "spawn.no-spawn-set", null);
            return true;
        }

        if (args.length > 0) {
            if (!sender.hasPermission("stonespawn.admin")) {
                mm.sendChat(sender, "general.no-permission", null);
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                mm.sendChat(sender, "general.player-not-found", Map.of("player", args[0]));
                return true;
            }

            if (!plugin.getWorldRestrictionManager().isAllowed(target.getWorld())) {
                mm.sendChat(sender, "general.world-blocked", null);
                return true;
            }

            String initiatorName = sender.getName();
            plugin.getTeleportManager().startTeleport(target, spawn,
                    new TeleportContext(false, false, p -> mm.sendChat(p, "spawn.teleported-by", Map.of("player", initiatorName))));
            mm.sendChat(sender, "spawn.teleported-other", Map.of("player", target.getName()));
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

        if (!plugin.getWorldRestrictionManager().isAllowed(player.getWorld())) {
            mm.sendChat(player, "general.world-blocked", null);
            return true;
        }

        if (!player.hasPermission("stonespawn.bypass.cooldown")) {
            int cooldownSeconds = plugin.getConfigManager().getInt("command.cooldown-seconds", 10);
            int remaining = plugin.getCooldownManager().getRemaining(player.getUniqueId(), cooldownSeconds);
            if (remaining > 0) {
                mm.sendChat(player, "spawn.cooldown", Map.of("seconds", String.valueOf(remaining)));
                return true;
            }
            plugin.getCooldownManager().setUsed(player.getUniqueId());
        }

        plugin.getTeleportManager().startTeleport(player, spawn, TeleportContext.commandContext());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("stonespawn.admin")) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
