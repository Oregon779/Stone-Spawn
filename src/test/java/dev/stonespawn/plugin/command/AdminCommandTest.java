package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCommandTest extends PluginTestBase {

    @Test
    void setSpawnCentersOnBlockAndKeepsRotation() {
        TestPlayer admin = addAdmin("Admin");
        admin.teleport(new Location(world, 10.9, 65, -3.2, 90f, 10f));
        admin.performCommand("setspawn");

        Location spawn = plugin.getSpawnManager().getSpawn();
        assertEquals(10.5, spawn.getX());
        assertEquals(-3.5, spawn.getZ());
        assertEquals(90f, spawn.getYaw());
        assertEquals(List.of("[StoneSpawn] ✔ The spawn point has been set to your current location."), messages(admin));
    }

    @Test
    void namedSpawnIsStoredLowercase() {
        TestPlayer admin = addAdmin("Admin");
        admin.performCommand("setspawn Nether_Hub");
        assertTrue(plugin.getSpawnManager().hasSpawn("nether_hub"));
        assertEquals(List.of("nether_hub"), plugin.getSpawnManager().getSpawnNames());
    }

    @Test
    void invalidAndOverlongNamesAreRejected() {
        TestPlayer admin = addAdmin("Admin");
        admin.performCommand("setspawn a.b");
        admin.performCommand("setspawn " + "x".repeat(200));
        admin.performCommand("setspawn <red>evil");

        assertEquals(0, plugin.getSpawnManager().getSpawnCount());
        List<String> messages = messages(admin);
        assertEquals(3, messages.size());
        messages.forEach(m -> {
            assertTrue(m.contains("is not a valid name"), m);
            assertFalse(m.contains("<"), m);
            assertTrue(m.length() < 160, m);
        });
    }

    @Test
    void normalPlayersCannotSetOrDeleteSpawns() {
        spawnAt("hub", 1, 64, 1);
        TestPlayer player = addPlayer("Steve");
        player.performCommand("setspawn");
        player.performCommand("setspawn other");
        player.performCommand("delspawn hub");

        assertFalse(plugin.getSpawnManager().hasSpawn());
        assertTrue(plugin.getSpawnManager().hasSpawn("hub"));
        assertEquals(3, messages(player).stream().filter(m -> m.contains("don't have permission")).count());
    }

    @Test
    void deleteSpawnVariants() {
        spawnAt("hub", 1, 64, 1);
        TestPlayer admin = addAdmin("Admin");

        admin.performCommand("delspawn");
        admin.performCommand("delspawn nope");
        admin.performCommand("delspawn HUB");

        assertFalse(plugin.getSpawnManager().hasSpawn("hub"));
        assertEquals(List.of(
                "[StoneSpawn] ✖ Usage: /delspawn <name>",
                "[StoneSpawn] ✖ There is no spawn called nope.",
                "[StoneSpawn] ✔ The spawn hub has been deleted."), messages(admin));
    }

    @Test
    void consoleCanDeleteButNotSetSpawns() {
        spawnAt("hub", 1, 64, 1);
        server.dispatchCommand(server.getConsoleSender(), "setspawn");
        server.dispatchCommand(server.getConsoleSender(), "delspawn hub");
        assertFalse(plugin.getSpawnManager().hasSpawn());
        assertFalse(plugin.getSpawnManager().hasSpawn("hub"));
    }

    @Test
    void tabCompletionRespectsPermissions() {
        spawnAt(SpawnManager.MAIN_SPAWN, 1, 64, 1);
        spawnAt("hub", 2, 64, 2);
        TestPlayer player = addPlayer("Steve");
        TestPlayer admin = addAdmin("Admin");

        assertEquals(List.of("spawn", "hub"), server.getCommandTabComplete(player, "spawn "));
        assertTrue(server.getCommandTabComplete(admin, "spawn ").containsAll(List.of("spawn", "hub", "Steve", "Admin")));
        assertEquals(List.of("Steve"), server.getCommandTabComplete(admin, "spawn hub St"));
        assertTrue(server.getCommandTabComplete(player, "delspawn ").isEmpty());
        assertEquals(List.of("help"), server.getCommandTabComplete(player, "stonespawn "));
    }

    @Test
    void reloadRequiresAdminAndAppliesChanges() {
        TestPlayer player = addPlayer("Steve");
        TestPlayer admin = addAdmin("Admin");
        player.performCommand("stonespawn reload");
        admin.performCommand("stonespawn reload");
        assertTrue(messages(player).get(0).contains("don't have permission"));
        assertEquals(List.of("[StoneSpawn] ✔ Configuration and messages have been reloaded."), messages(admin));
    }
}
