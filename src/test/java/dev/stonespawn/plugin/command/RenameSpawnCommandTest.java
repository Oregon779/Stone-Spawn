package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameSpawnCommandTest extends PluginTestBase {

    @Test
    void renamesASpawnAndKeepsItsLocation() {
        Location hub = spawnAt("hub", 10.5, 70, -3.5);
        TestPlayer admin = addAdmin("Admin");

        admin.performCommand("renamespawn HUB Nether_Hub");

        assertFalse(plugin.getSpawnManager().hasSpawn("hub"));
        assertEquals(hub, plugin.getSpawnManager().getSpawn("nether_hub"));
        assertEquals(List.of("[StoneSpawn] ✔ The spawn hub is now called nether_hub."), messages(admin));
    }

    @Test
    void renamedMainSpawnStaysTheMainSpawn() {
        Location main = spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        spawnAt("arena", 5.5, 64, 5.5);
        configure(Map.of("spawn.death.enabled", true));
        TestPlayer admin = addAdmin("Admin");

        admin.performCommand("renamespawn spawn lobby");

        assertEquals(List.of("[StoneSpawn] ✔ The main spawn spawn is now called lobby - it stays the main spawn."),
                messages(admin));
        assertEquals("lobby", plugin.getSpawnManager().getMainSpawnName());
        assertEquals(main, plugin.getSpawnManager().getSpawn());
        assertEquals(List.of("lobby", "arena"), plugin.getSpawnManager().getSpawnNames(), "main spawn is listed first");

        PlayerRespawnEvent respawn = new PlayerRespawnEvent(admin, admin.getLocation(), false);
        server.getPluginManager().callEvent(respawn);
        assertTrue(sameBlock(respawn.getRespawnLocation(), main), "automatic teleports still use it");

        admin.performCommand("spawn lobby");
        server.getScheduler().performTicks(3 * 20 + 5);
        assertTrue(sameBlock(admin.getLocation(), main));
    }

    @Test
    void setSpawnWithoutNameMovesTheRenamedMainSpawn() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        TestPlayer admin = addAdmin("Admin");
        admin.performCommand("renamespawn spawn lobby");

        admin.teleport(new Location(world, 20.5, 80, 20.5));
        admin.performCommand("setspawn");

        assertEquals(List.of("lobby"), plugin.getSpawnManager().getSpawnNames(), "no second spawn called 'spawn'");
        assertEquals(20.5, plugin.getSpawnManager().getSpawn().getX());
    }

    @Test
    void refusesTakenUnknownAndInvalidNames() {
        spawnAt("hub", 1.5, 64, 1.5);
        spawnAt("arena", 2.5, 64, 2.5);
        TestPlayer admin = addAdmin("Admin");

        admin.performCommand("renamespawn hub arena");
        admin.performCommand("renamespawn nowhere other");
        admin.performCommand("renamespawn hub a.b");
        admin.performCommand("renamespawn hub");

        assertEquals(List.of(
                "[StoneSpawn] ✖ A spawn called arena already exists.",
                "[StoneSpawn] ✖ There is no spawn called nowhere.",
                "[StoneSpawn] ✖ a.b is not a valid name. Allowed are a-z, 0-9, _ and - (max. 32 characters).",
                "[StoneSpawn] ✖ Usage: /renamespawn <name> <new name>"), messages(admin));
        assertEquals(List.of("arena", "hub"), plugin.getSpawnManager().getSpawnNames());
    }

    @Test
    void normalPlayersCannotRename() {
        spawnAt("hub", 1.5, 64, 1.5);
        TestPlayer player = addPlayer("Steve");
        player.performCommand("renamespawn hub mine");
        assertTrue(plugin.getSpawnManager().hasSpawn("hub"));
        assertEquals(List.of("[StoneSpawn] ✖ You don't have permission to do this."), messages(player));
        assertEquals(List.of(), plugin.getCommand("renamespawn").tabComplete(player, "renamespawn", new String[]{""}));
    }

    @Test
    void tabCompletesExistingSpawns() {
        spawnAt("hub", 1.5, 64, 1.5);
        spawnAt("arena", 2.5, 64, 2.5);
        TestPlayer admin = addAdmin("Admin");
        assertEquals(List.of("hub"), plugin.getCommand("renamespawn").tabComplete(admin, "renamespawn", new String[]{"h"}));
    }

    @Test
    void ownGuiLookMovesToTheNewNameWithoutReformattingTheFile() throws IOException {
        spawnAt("hub", 1.5, 64, 1.5);
        File file = new File(plugin.getDataFolder(), "gui.yml");
        String withOwnLook = Files.readString(file.toPath()).replace("spawns: {}", """
                spawns:
                  # My favourite spawn
                  hub:
                    slot: 13
                    name: "&a&lHub"
                  arena:
                    hidden: true""");
        Files.writeString(file.toPath(), withOwnLook);
        plugin.reload();

        addAdmin("Admin").performCommand("renamespawn hub lobby");

        assertEquals(withOwnLook.replace("  hub:\n", "  lobby:\n"), Files.readString(file.toPath()));
        TestPlayer player = addPlayer("Steve");
        spawnAt("second", 3.5, 64, 3.5);
        player.performCommand("spawn");
        assertEquals("Hub", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.getOpenInventory().getTopInventory().getItem(13).getItemMeta().displayName()));
    }
}
