package dev.stonespawn.plugin.intro;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** With resource-pack.wait, the intro starts the set interval after the player's resource packs have loaded. */
class IntroResourcePackTest extends PluginTestBase {

    // The interval: start-delay-ticks, counted from the moment the pack has loaded.
    private static final int INTERVAL = 20;

    @BeforeEach
    void introWaitingForThePack() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        TestPlayer admin = addAdmin("Builder");
        admin.teleport(new Location(world, 10.5, 80, 10.5));
        admin.performCommand("stonespawn intro addpoint tower");
        setIntro(Map.of("enabled", true, "start-delay-ticks", INTERVAL,
                "resource-pack.wait", true, "resource-pack.max-wait-seconds", 30));
    }

    private void setIntro(Map<String, Object> values) {
        File file = new File(plugin.getDataFolder(), "intro.yml");
        YamlConfiguration intro = YamlConfiguration.loadConfiguration(file);
        values.forEach(intro::set);
        try {
            intro.save(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        plugin.reload();
    }

    private TestPlayer newPlayer(Status loadedBeforeJoining) {
        TestPlayer player = new TestPlayer(server, "Newbie");
        player.setResourcePackStatus(loadedBeforeJoining);
        server.addPlayer(player);
        return player;
    }

    private void pack(TestPlayer player, UUID pack, Status status) {
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player, pack, status));
    }

    private static boolean watching(TestPlayer player) {
        return player.getGameMode() == GameMode.SPECTATOR;
    }

    @Test
    void startsTheIntervalAfterThePackHasLoaded() {
        TestPlayer player = newPlayer(null);
        UUID pack = UUID.randomUUID();
        server.getScheduler().performTicks(60);
        assertFalse(watching(player), "the plugin hasn't sent its pack yet");

        pack(player, pack, Status.ACCEPTED);
        pack(player, pack, Status.DOWNLOADED);
        server.getScheduler().performTicks(40);
        assertFalse(watching(player), "still loading");

        pack(player, pack, Status.SUCCESSFULLY_LOADED);
        server.getScheduler().performTicks(INTERVAL - 8);
        assertFalse(watching(player), "the interval isn't over yet");
        server.getScheduler().performTicks(15);
        assertTrue(watching(player));
    }

    @Test
    void aSecondPackIsWaitedForToo() {
        TestPlayer player = newPlayer(null);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        pack(player, first, Status.ACCEPTED);
        pack(player, first, Status.SUCCESSFULLY_LOADED);
        server.getScheduler().performTicks(10);
        pack(player, second, Status.ACCEPTED);

        server.getScheduler().performTicks(60);
        assertFalse(watching(player), "the second pack is still loading");

        pack(player, second, Status.SUCCESSFULLY_LOADED);
        server.getScheduler().performTicks(INTERVAL + 7);
        assertTrue(watching(player));
    }

    @Test
    void aDeclinedOrFailedPackStillGetsTheIntro() {
        TestPlayer player = newPlayer(null);
        pack(player, UUID.randomUUID(), Status.DECLINED);
        server.getScheduler().performTicks(INTERVAL + 7);
        assertTrue(watching(player));
    }

    @Test
    void withoutAnyPackTheIntroStartsAfterTheMaximumWait() {
        setIntro(Map.of("resource-pack.max-wait-seconds", 3));
        TestPlayer player = newPlayer(null);
        server.getScheduler().performTicks(50);
        assertFalse(watching(player));
        server.getScheduler().performTicks(20);
        assertTrue(watching(player));
    }

    @Test
    void aPackLoadedBeforeJoiningOnlyNeedsTheInterval() {
        TestPlayer player = newPlayer(Status.SUCCESSFULLY_LOADED);
        server.getScheduler().performTicks(INTERVAL + 7);
        assertTrue(watching(player));
    }

    @Test
    void quittingWhileWaitingStartsNothing() {
        TestPlayer player = newPlayer(null);
        pack(player, UUID.randomUUID(), Status.ACCEPTED);
        player.disconnect();
        server.getScheduler().performTicks(30 * 20 + 20);
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
    }
}
