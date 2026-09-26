package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Collection;

public class CommandVisibilityListener implements Listener {

    private final StoneSpawn plugin;

    public CommandVisibilityListener(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        Collection<String> commands = event.getCommands();

        if (!player.hasPermission("stonespawn.admin")) {
            removeCommand(commands, "setspawn");
            removeCommand(commands, "delspawn");
        }
        if (!player.hasPermission("stonespawn.use")) {
            removeCommand(commands, "spawn");
        }
    }

    private void removeCommand(Collection<String> commands, String label) {
        commands.remove(label);
        commands.remove(plugin.getName().toLowerCase() + ":" + label);
    }
}
