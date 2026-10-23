package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticTeleportTest extends PluginTestBase {

    private Location spawn;

    @BeforeEach
    void mainSpawn() {
        spawn = spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
    }

    @Test
    void voidSendsPlayersToSpawnButLeavesSpectatorsAlone() {
        configure(Map.of("spawn.void.enabled", true));
        TestPlayer falling = addPlayer("Falling");
        TestPlayer spectator = addPlayer("Spectator");
        spectator.setGameMode(GameMode.SPECTATOR);

        falling.simulatePlayerMove(new Location(world, 0.5, -70, 0.5));
        spectator.simulatePlayerMove(new Location(world, 0.5, -70, 0.5));

        assertTrue(sameBlock(falling.getLocation(), spawn));
        assertFalse(sameBlock(spectator.getLocation(), spawn), "spectators can't die in the void");
    }

    @Test
    void worldChangeSendsPlayersToSpawn() {
        configure(Map.of("spawn.world-change.enabled", true));
        WorldMock other = server.addSimpleWorld("other");
        TestPlayer player = addPlayer("Steve");

        player.teleport(new Location(other, 5.5, 64, 5.5));

        assertTrue(sameBlock(player.getLocation(), spawn));
    }
}
