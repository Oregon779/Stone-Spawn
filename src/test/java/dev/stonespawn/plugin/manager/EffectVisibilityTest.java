package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Countdown and arrival effects are personal: only the teleporting player sees and hears them, not the
 * players standing next to them or waiting at the spawn.
 */
class EffectVisibilityTest extends PluginTestBase {

    private TestPlayer teleporting;
    private TestPlayer standingNextToThem;
    private TestPlayer waitingAtSpawn;

    @BeforeEach
    void players() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        // The particle ring at the spawn is meant for everyone; switched off so it can't hide a leak.
        configure(Map.of("spawn.marker.enabled", false, "spawn.death.enabled", true));
        teleporting = addPlayer("Teleporting");
        standingNextToThem = addPlayer("Neighbour");
        standingNextToThem.teleport(new Location(world, 1.5, 64, 0.5));
        waitingAtSpawn = addPlayer("AtSpawn");
        waitingAtSpawn.teleport(new Location(world, 101.5, 70, 100.5));
        world.clearSpawnedParticles();
    }

    private void assertOnlyTheTeleportingPlayerNoticedAnything() {
        assertTrue(teleporting.receivedParticles().contains(Particle.DUST), "control: the effects did run");
        assertFalse(teleporting.getHeardSounds().isEmpty(), "control: the sounds did play");

        for (TestPlayer other : new TestPlayer[]{standingNextToThem, waitingAtSpawn}) {
            assertEquals(0, other.getHeardSounds().size(), other.getName() + " heard " + other.getHeardSounds());
            assertTrue(other.receivedParticles().isEmpty(), other.getName() + " saw " + other.receivedParticles());
        }
        assertTrue(world.getSpawnedParticles().isEmpty(), "no particles may be sent to the whole world");
    }

    @Test
    void countdownAndArrivalAreOnlyForTheTeleportingPlayer() {
        teleporting.performCommand("spawn");
        server.getScheduler().performTicks(3 * 20 + 30);

        assertTrue(sameBlock(teleporting.getLocation(), plugin.getSpawnManager().getSpawn()));
        assertOnlyTheTeleportingPlayerNoticedAnything();
    }

    @Test
    void arrivalAfterDeathIsOnlyForTheRespawningPlayer() {
        server.getPluginManager().callEvent(new PlayerRespawnEvent(teleporting, teleporting.getLocation(), false));
        server.getScheduler().performTicks(30);

        assertOnlyTheTeleportingPlayerNoticedAnything();
    }
}
