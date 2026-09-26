package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.PluginTestBase;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.WorldLoadEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnManagerTest extends PluginTestBase {

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    private SpawnManager reloadedFromDisk() {
        SpawnManager fresh = new SpawnManager(plugin);
        fresh.load();
        return fresh;
    }

    @Test
    void namesAreSortedMainFirstThenAlphabetically() {
        spawnAt("zeta", 1, 64, 1);
        spawnAt("alpha", 2, 64, 2);
        spawnAt(SpawnManager.MAIN_SPAWN, 0, 64, 0);

        assertEquals(List.of("spawn", "alpha", "zeta"), plugin.getSpawnManager().getSpawnNames());
        assertEquals(3, plugin.getSpawnManager().getAllSpawnLocations().size());
    }

    @Test
    void namesAreCaseInsensitiveAndValidated() {
        spawnAt("Hub", 5, 70, 5);
        assertTrue(plugin.getSpawnManager().hasSpawn("HUB"));
        assertEquals(5.0, plugin.getSpawnManager().getSpawn("hub").getX());

        assertTrue(SpawnManager.isValidName("nether_hub-2"));
        assertFalse(SpawnManager.isValidName("a.b"), "dots would break the YAML path");
        assertFalse(SpawnManager.isValidName("with space"));
        assertFalse(SpawnManager.isValidName("<red>x"));
        assertFalse(SpawnManager.isValidName("x".repeat(33)));
        assertFalse(SpawnManager.isValidName(""));
    }

    @Test
    void returnedLocationsAreCopies() {
        spawnAt("hub", 5, 70, 5);
        plugin.getSpawnManager().getSpawn("hub").add(100, 0, 0);
        assertEquals(5.0, plugin.getSpawnManager().getSpawn("hub").getX());
    }

    @Test
    void deleteRemovesSpawn() {
        spawnAt("hub", 5, 70, 5);
        assertTrue(plugin.getSpawnManager().deleteSpawn("HUB"));
        assertFalse(plugin.getSpawnManager().hasSpawn("hub"));
        assertFalse(plugin.getSpawnManager().deleteSpawn("hub"));
        assertTrue(plugin.getSpawnManager().getAllSpawnLocations().isEmpty());
    }

    @Test
    void spawnsAndJoinedPlayersSurviveRestart() {
        spawnAt(SpawnManager.MAIN_SPAWN, 0.5, 64, 0.5);
        spawnAt("hub", 10.5, 80, -3.5);
        UUID joined = UUID.randomUUID();
        plugin.getSpawnManager().markJoined(joined);
        plugin.getSpawnManager().flush();

        SpawnManager fresh = reloadedFromDisk();
        assertEquals(List.of("spawn", "hub"), fresh.getSpawnNames());
        assertEquals(new Location(world, 10.5, 80, -3.5), fresh.getSpawn("hub"));
        assertTrue(fresh.hasJoinedBefore(joined));
    }

    @Test
    void asyncSavesEndWithNewestState() {
        for (int i = 0; i < 20; i++) {
            spawnAt("s" + i, i, 64, i);
        }
        plugin.getSpawnManager().deleteSpawn("s0");
        server.getScheduler().waitAsyncTasksFinished();

        SpawnManager fresh = reloadedFromDisk();
        assertEquals(19, fresh.getSpawnCount());
        assertFalse(fresh.hasSpawn("s0"));
        assertTrue(fresh.hasSpawn("s19"));
    }

    @Test
    void firstJoinsAreBatchedAndFlushedOnShutdown() {
        UUID joined = UUID.randomUUID();
        plugin.getSpawnManager().markJoined(joined);
        assertFalse(reloadedFromDisk().hasJoinedBefore(joined), "first joins are written in a batch, not per join");

        server.getPluginManager().disablePlugin(plugin);
        assertTrue(reloadedFromDisk().hasJoinedBefore(joined), "pending changes must be written on shutdown");
    }

    @Test
    void legacySingleSpawnIsMigrated() throws Exception {
        String legacy = """
                spawn:
                  world: world
                  x: 3.5
                  y: 65.0
                  z: -7.5
                  yaw: 90.0
                  pitch: 0.0
                joined-players:
                - 11111111-1111-1111-1111-111111111111
                """;
        Files.writeString(dataFile().toPath(), legacy);

        SpawnManager migrated = reloadedFromDisk();
        assertTrue(migrated.hasSpawn());
        assertEquals(3.5, migrated.getSpawn().getX());
        assertEquals(90f, migrated.getSpawn().getYaw());
        migrated.flush();

        YamlConfiguration written = YamlConfiguration.loadConfiguration(dataFile());
        assertFalse(written.contains("spawn"), "old format must be removed after migration");
        assertTrue(written.contains("spawns.spawn.world"));
        assertEquals(1, written.getStringList("joined-players").size());
    }

    @Test
    void corruptDataFileIsBackedUpInsteadOfOverwritten() throws IOException {
        String broken = "spawns:\n  hub: [unclosed\n";
        Files.writeString(dataFile().toPath(), broken);

        SpawnManager manager = reloadedFromDisk();
        assertEquals(0, manager.getSpawnCount());

        File[] backups = plugin.getDataFolder().listFiles((dir, name) -> name.startsWith("data.yml.broken-"));
        assertNotNull(backups);
        assertEquals(1, backups.length);
        assertEquals(broken, Files.readString(backups[0].toPath()));
    }

    @Test
    void spawnInUnloadedWorldAppearsOnceWorldLoads() throws IOException {
        Files.writeString(dataFile().toPath(), """
                spawns:
                  nether_hub:
                    world: late_world
                    x: 1.0
                    y: 70.0
                    z: 1.0
                    yaw: 0.0
                    pitch: 0.0
                """);
        SpawnManager manager = reloadedFromDisk();
        assertTrue(manager.hasSpawn("nether_hub"));
        assertNull(manager.getSpawn("nether_hub"));
        assertTrue(manager.getAllSpawnLocations().isEmpty());

        WorldMock late = server.addSimpleWorld("late_world");
        manager.onWorldLoad(new WorldLoadEvent(late));
        assertNotNull(manager.getSpawn("nether_hub"));
        assertEquals(1, manager.getAllSpawnLocations().size());
    }

    @Test
    void noTempFileLeftBehind() {
        spawnAt("hub", 1, 64, 1);
        plugin.getSpawnManager().flush();
        server.getScheduler().waitAsyncTasksFinished();
        String[] files = plugin.getDataFolder().list();
        assertNotNull(files);
        assertFalse(Arrays.asList(files).contains("data.yml.tmp"));
    }
}
