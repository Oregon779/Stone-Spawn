package dev.stonespawn.plugin;

import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleTest extends PluginTestBase {

    @Test
    void reloadDoesNotRegisterListenersTwice() {
        int before = HandlerList.getRegisteredListeners(plugin).size();
        for (int i = 0; i < 3; i++) {
            addAdmin("Admin" + i).performCommand("stonespawn reload");
        }
        assertEquals(before, HandlerList.getRegisteredListeners(plugin).size());
    }

    @Test
    void reloadKeepsExactlyOneMarkerTask() {
        spawnAt(SpawnManager.MAIN_SPAWN, 0.5, 64, 0.5);
        plugin.reload();
        int afterFirstReload = plugin.getServer().getScheduler().getPendingTasks().size();
        assertEquals(2, afterFirstReload, "spawn marker + update checker");
        plugin.reload();
        plugin.reload();
        assertEquals(afterFirstReload, plugin.getServer().getScheduler().getPendingTasks().size());
    }

    @Test
    void disablingStopsCountdownsAndTasks() {
        Location spawn = spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");

        server.getPluginManager().disablePlugin(plugin);
        assertTrue(server.getScheduler().getPendingTasks().stream().noneMatch(t -> t.getOwner() == plugin));
        server.getScheduler().performTicks(100);
        assertFalse(sameBlock(player.getLocation(), spawn));
    }

    @Test
    void deathRespawnRespectsArrivalMessageSwitch() {
        Location spawn = spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        configure(Map.of("spawn.death.enabled", true,
                "teleport.arrival.notification", "CHAT",
                "teleport.arrival.message-enabled", false));
        TestPlayer player = addPlayer("Steve");

        PlayerRespawnEvent event = new PlayerRespawnEvent(player, player.getLocation(), false);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performTicks(5);

        assertTrue(sameBlock(event.getRespawnLocation(), spawn));
        assertTrue(messages(player).isEmpty(), "arrival message is switched off in config.yml");

        configure(Map.of("teleport.arrival.message-enabled", true));
        server.getPluginManager().callEvent(new PlayerRespawnEvent(player, player.getLocation(), false));
        server.getScheduler().performTicks(5);
        assertEquals(1, messages(player).size());
    }

    @Test
    void bedSpawnIsKeptByDefault() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        configure(Map.of("spawn.death.enabled", true));
        TestPlayer player = addPlayer("Steve");
        Location bed = new Location(world, -50, 64, -50);

        PlayerRespawnEvent event = new PlayerRespawnEvent(player, bed, true);
        server.getPluginManager().callEvent(event);
        assertTrue(sameBlock(event.getRespawnLocation(), bed));
    }

    @Test
    void invalidConfigValuesFallBackToDefaults() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        configure(Map.of("teleport.delay-seconds", "three",
                "command.cooldown-seconds", "ten",
                "spawn.join.mode", "SOMETIMES",
                "spawn.marker.particle", "DUST",
                "teleport.countdown.sound", "NOT_A_SOUND",
                "worlds.mode", 42));
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        server.getScheduler().performTicks(3 * 20 + 5);
        assertTrue(sameBlock(player.getLocation(), plugin.getSpawnManager().getSpawn()));
    }
}
