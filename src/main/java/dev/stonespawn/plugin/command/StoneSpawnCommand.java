package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.intro.IntroManager;
import dev.stonespawn.plugin.manager.MessageManager;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StoneSpawnCommand implements CommandExecutor, TabCompleter {

    // How far away the block a camera point should look at may be.
    private static final int LOOK_AT_RANGE = 256;

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

        switch (args[0].toLowerCase(Locale.ROOT)) {
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
            case "intro" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                handleIntro(sender, args);
            }
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleIntro(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        IntroManager intro = plugin.getIntroManager();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "play" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2])
                        : sender instanceof Player player ? player : null;
                if (target == null) {
                    if (args.length > 2) {
                        mm.sendChat(sender, "general.player-not-found", Map.of("player", MessageManager.sanitizeInput(args[2])));
                    } else {
                        mm.sendChat(sender, "general.player-only", null);
                    }
                    return;
                }
                switch (intro.start(target)) {
                    case STARTED -> mm.sendChat(sender, "intro.playing", Map.of("player", target.getName()));
                    case ALREADY_RUNNING -> mm.sendChat(sender, "intro.already-playing", Map.of("player", target.getName()));
                    case NO_POINTS -> mm.sendChat(sender, "intro.no-points", null);
                }
            }
            case "addpoint" -> {
                if (!(sender instanceof Player player)) {
                    mm.sendChat(sender, "general.player-only", null);
                    return;
                }
                if (args.length < 3) {
                    mm.sendChat(sender, "intro.usage", null);
                    return;
                }
                if (!SpawnManager.isValidName(args[2])) {
                    mm.sendChat(sender, "intro.invalid-name", Map.of("point", MessageManager.sanitizeInput(args[2])));
                    return;
                }
                String name = SpawnManager.normalize(args[2]);
                // The camera sits where the admin's eyes are and looks where they look.
                switch (intro.setPoint(name, player.getEyeLocation())) {
                    case ADDED -> mm.sendChat(sender, "intro.point-added", Map.of("point", name));
                    case MOVED -> mm.sendChat(sender, "intro.point-moved", Map.of("point", name));
                    default -> mm.sendChat(sender, "intro.save-failed", null);
                }
            }
            case "lookat" -> handleLookAt(sender, args);
            case "preview" -> {
                if (!(sender instanceof Player player)) {
                    mm.sendChat(sender, "general.player-only", null);
                    return;
                }
                switch (intro.togglePreview(player)) {
                    case SHOWN -> mm.sendChat(sender, "intro.preview-shown", null);
                    case HIDDEN -> mm.sendChat(sender, "intro.preview-hidden", null);
                    case NO_POINTS -> mm.sendChat(sender, "intro.no-points", null);
                }
            }
            case "removepoint" -> {
                if (args.length < 3) {
                    mm.sendChat(sender, "intro.usage", null);
                    return;
                }
                String name = SpawnManager.normalize(args[2]);
                if (intro.removePoint(name)) {
                    mm.sendChat(sender, "intro.point-removed", Map.of("point", name));
                } else {
                    mm.sendChat(sender, "intro.point-not-found", Map.of("point", MessageManager.sanitizeInput(args[2])));
                }
            }
            case "list" -> {
                List<Map<String, String>> points = intro.describePoints();
                if (points.isEmpty()) {
                    mm.sendChat(sender, "intro.list-empty", null);
                    return;
                }
                mm.sendChat(sender, "intro.list-header", null);
                points.forEach(point -> mm.sendRaw(sender, "intro.list-entry", point));
            }
            default -> mm.sendChat(sender, "intro.usage", null);
        }
    }

    private void handleLookAt(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        IntroManager intro = plugin.getIntroManager();
        if (!(sender instanceof Player player)) {
            mm.sendChat(sender, "general.player-only", null);
            return;
        }
        if (args.length < 3) {
            mm.sendChat(sender, "intro.usage", null);
            return;
        }
        String name = SpawnManager.normalize(args[2]);
        String world = intro.pointWorld(name);
        if (world == null) {
            mm.sendChat(sender, "intro.point-not-found", Map.of("point", MessageManager.sanitizeInput(args[2])));
            return;
        }
        if (args.length > 3 && args[3].equalsIgnoreCase("clear")) {
            boolean cleared = intro.setLookAt(name, null) != IntroManager.PointResult.SAVE_FAILED;
            mm.sendChat(sender, cleared ? "intro.lookat-cleared" : "intro.save-failed", Map.of("point", name));
            return;
        }
        if (!player.getWorld().getName().equals(world)) {
            mm.sendChat(sender, "intro.lookat-other-world", Map.of("point", name, "world", world));
            return;
        }
        Block block = player.getTargetBlockExact(LOOK_AT_RANGE);
        if (block == null) {
            mm.sendChat(sender, "intro.lookat-no-block", Map.of("range", String.valueOf(LOOK_AT_RANGE)));
            return;
        }
        Vector target = block.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
        if (intro.setLookAt(name, target) == IntroManager.PointResult.SAVE_FAILED) {
            mm.sendChat(sender, "intro.save-failed", null);
            return;
        }
        mm.sendChat(sender, "intro.lookat-set", Map.of("point", name, "x", String.valueOf(block.getX()),
                "y", String.valueOf(block.getY()), "z", String.valueOf(block.getZ())));
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
            mm.sendRaw(sender, "help.renamespawn", null);
        }
        if (sender.hasPermission("stonespawn.use")) {
            mm.sendRaw(sender, "help.spawn", null);
            mm.sendRaw(sender, "help.spawn-named", null);
        }
        if (isAdmin) {
            mm.sendRaw(sender, "help.reload", null);
            mm.sendRaw(sender, "help.checkupdate", null);
            mm.sendRaw(sender, "help.intro", null);
        }
        mm.sendRaw(sender, "help.help", null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("stonespawn.admin");
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            Stream<String> options = isAdmin ? Stream.of("reload", "help", "checkupdate", "intro") : Stream.of("help");
            return options.filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (!isAdmin || !args[0].equalsIgnoreCase("intro")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return filter(Stream.of("play", "addpoint", "removepoint", "lookat", "list", "preview"), args[1]);
        }
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "play" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[2]);
                case "addpoint", "removepoint", "lookat" -> filter(plugin.getIntroManager().pointNames().stream(), args[2]);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("lookat")) {
            return filter(Stream.of("clear"), args[3]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(Stream<String> options, String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
