package dev.stonespawn.plugin.intro;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntroTest extends PluginTestBase {

    // With 1 second per stay and flight, two points take 20 + 20 + 20 ticks.
    private static final int WHOLE_INTRO_TICKS = 70;

    private Location spawn;
    private Location tower;
    private Location market;

    @BeforeEach
    void twoCameraPoints() {
        spawn = spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        TestPlayer admin = addAdmin("Builder");
        admin.teleport(new Location(world, 10.5, 80, 10.5, 90f, 20f));
        tower = admin.getEyeLocation();
        admin.performCommand("stonespawn intro addpoint tower");
        admin.teleport(new Location(world, 30.5, 90, -10.5, -90f, 0f));
        market = admin.getEyeLocation();
        admin.performCommand("stonespawn intro addpoint market");
        setIntro(Map.of("enabled", true, "fly-seconds", 1.0, "stay-seconds", 1.0, "start-delay-ticks", 5,
                "points.tower.title", "&bWelcome, {player}!", "points.market.chat", java.util.List.of("&7The market")));
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

    private TestPlayer joinForTheFirstTime(String name) {
        TestPlayer player = new TestPlayer(server, name);
        server.addPlayer(player);
        return player;
    }

    private static boolean near(Location a, Location b) {
        return a.getWorld() == b.getWorld() && a.distanceSquared(b) < 0.01;
    }

    private long cameras() {
        return world.getEntities().stream().filter(ItemDisplay.class::isInstance).count();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void firstJoinFliesTheCameraAlongThePointsAndEndsAtTheSpawnWithTheBook() {
        TestPlayer newbie = joinForTheFirstTime("Newbie");
        server.getScheduler().performTicks(6);
        assertEquals(GameMode.SPECTATOR, newbie.getGameMode());
        assertNull(newbie.getSpectatorTarget(), "attached only once the client knows the camera entity");
        server.getScheduler().performTicks(2);

        Entity camera = newbie.getSpectatorTarget();
        assertInstanceOf(ItemDisplay.class, camera, "the player looks through the camera entity");
        assertTrue(near(camera.getLocation(), tower));
        assertEquals("Welcome, Newbie!", plain(newbie.shownTitles().getFirst().title()));

        server.getScheduler().performTicks(28);
        double x = camera.getLocation().getX();
        assertTrue(x > tower.getX() + 1 && x < market.getX() - 1, "half way between the points: " + x);

        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);
        assertEquals(GameMode.SURVIVAL, newbie.getGameMode());
        assertTrue(sameBlock(newbie.getLocation(), spawn), "back where the intro started: the first-join spawn");
        assertEquals(1, newbie.openedBooks().size());
        assertTrue(messages(newbie).contains("The market"));
        assertEquals(0, cameras(), "the camera entity is gone");
    }

    @Test
    void waitsForATeleportThatIsStillUnderway() {
        configure(Map.of("spawn.join.mode", "NEVER"));
        TestPlayer newbie = joinForTheFirstTime("Newbie");
        newbie.performCommand("spawn");

        server.getScheduler().performTicks(20);
        assertEquals(GameMode.SURVIVAL, newbie.getGameMode(), "the /spawn countdown runs first");

        server.getScheduler().performTicks(3 * 20);
        assertEquals(GameMode.SPECTATOR, newbie.getGameMode());
        server.getScheduler().performTicks(WHOLE_INTRO_TICKS + 20);
        assertTrue(sameBlock(newbie.getLocation(), spawn), "the intro started - and ends - at the spawn");
    }

    @Test
    void pointsInOtherWorldsDontTriggerTheWorldChangeTeleport() {
        org.mockbukkit.mockbukkit.world.WorldMock sky = server.addSimpleWorld("sky");
        TestPlayer admin = addAdmin("Admin");
        admin.teleport(new Location(sky, 0.5, 120, 0.5));
        Location skyPoint = admin.getEyeLocation();
        admin.performCommand("stonespawn intro addpoint sky");
        admin.teleport(new Location(world, 1.5, 64, 1.5));
        configure(Map.of("spawn.world-change.enabled", true));

        TestPlayer player = addPlayer("Steve");
        Location start = player.getLocation();
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(2 + 20 + 20 + 22);
        assertTrue(near(player.getLocation(), skyPoint), "reached the point in the other world");
        assertEquals(GameMode.SPECTATOR, player.getGameMode());

        server.getScheduler().performTicks(40);
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertTrue(sameBlock(player.getLocation(), start), "back where it started, not at the spawn");
    }

    @Test
    void whenTheWayBackIsGoneThePlayerLandsAtTheSpawn() {
        org.mockbukkit.mockbukkit.world.WorldMock temporary = server.addSimpleWorld("temporary");
        TestPlayer player = addPlayer("Steve");
        player.teleport(new Location(temporary, 3.5, 64, 3.5));
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(3);

        server.removeWorld(temporary);
        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);

        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertTrue(sameBlock(player.getLocation(), spawn), "not left in mid-air at the camera");
    }

    @Test
    void bedrockPlayersJumpFromPointToPoint() {
        TestPlayer bedrock = addBedrockPlayer("BedrockSteve");
        Location start = bedrock.getLocation();
        assertEquals(IntroManager.StartResult.STARTED, plugin.getIntroManager().start(bedrock));
        server.getScheduler().performTicks(2);

        assertNull(bedrock.getSpectatorTarget());
        assertEquals(0, cameras());
        assertTrue(near(bedrock.getLocation(), tower));

        server.getScheduler().performTicks(25);
        assertTrue(near(bedrock.getLocation(), tower), "stays for the time the flight would take");
        server.getScheduler().performTicks(20);
        assertTrue(near(bedrock.getLocation(), market), "then jumps, without a flight");

        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);
        assertEquals(GameMode.SURVIVAL, bedrock.getGameMode());
        assertTrue(sameBlock(bedrock.getLocation(), start));
    }

    @Test
    void shiftSkipsTheIntro() {
        TestPlayer player = addPlayer("Steve");
        Location start = player.getLocation();
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(3);

        PlayerToggleSneakEvent sneak = new PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);
        server.getScheduler().performTicks(2);

        assertFalse(sneak.isCancelled());
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertTrue(sameBlock(player.getLocation(), start));
        assertEquals(1, player.openedBooks().size(), "skipping still gives the book");
        assertEquals(0, cameras());
    }

    @Test
    void shiftIsIgnoredWhenSkippingIsOff() {
        setIntro(Map.of("skippable", false));
        TestPlayer player = addPlayer("Steve");
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(3);

        PlayerToggleSneakEvent sneak = new PlayerToggleSneakEvent(player, true);
        server.getPluginManager().callEvent(sneak);

        assertTrue(sneak.isCancelled(), "the camera can't be left");
        assertEquals(GameMode.SPECTATOR, player.getGameMode());
    }

    @Test
    void teleportByAnythingElseEndsTheIntroWhereThePlayerWasSent() {
        TestPlayer player = addPlayer("Steve");
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(3);

        Location elsewhere = new Location(world, -50.5, 64, -50.5);
        player.teleport(elsewhere);

        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertTrue(sameBlock(player.getLocation(), elsewhere));
        assertEquals(0, cameras());
        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);
        assertTrue(sameBlock(player.getLocation(), elsewhere), "the intro doesn't pull them back later");
    }

    @Test
    void gameModeChangedByAnAdminIsKept() {
        TestPlayer player = addPlayer("Steve");
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(3);

        player.setGameMode(GameMode.CREATIVE);
        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);

        assertEquals(GameMode.CREATIVE, player.getGameMode());
        assertEquals(0, cameras());
    }

    @Test
    void quittingAndShutdownBringThePlayerBack() {
        TestPlayer quitter = addPlayer("Quitter");
        Location quitterStart = quitter.getLocation();
        TestPlayer stayer = addPlayer("Stayer");
        stayer.teleport(new Location(world, 5.5, 64, 5.5));
        Location stayerStart = stayer.getLocation();
        plugin.getIntroManager().start(quitter);
        plugin.getIntroManager().start(stayer);
        server.getScheduler().performTicks(3);

        server.getPluginManager().callEvent(new PlayerQuitEvent(quitter, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(GameMode.SURVIVAL, quitter.getGameMode());
        assertTrue(sameBlock(quitter.getLocation(), quitterStart));

        server.getPluginManager().disablePlugin(plugin);
        assertEquals(GameMode.SURVIVAL, stayer.getGameMode());
        assertTrue(sameBlock(stayer.getLocation(), stayerStart));
        assertEquals(0, cameras());
    }

    @Test
    void playerCutOffByACrashIsBroughtBackOnTheNextJoin() {
        TestPlayer player = addPlayer("Steve");
        Location start = new Location(world, 7.5, 64, 7.5);
        // What a crash mid-intro leaves behind: spectator mode plus the remembered game mode and position.
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(tower);
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "intro_game_mode"), PersistentDataType.STRING, "ADVENTURE");
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "intro_return"), PersistentDataType.STRING,
                "world;7.5;64.0;7.5;0.0;0.0");

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertEquals(GameMode.ADVENTURE, player.getGameMode());
        assertTrue(sameBlock(player.getLocation(), start));
        assertTrue(player.getPersistentDataContainer().getKeys().isEmpty());

        // Same after a crash, but the world of the way back no longer exists.
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(tower);
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "intro_game_mode"), PersistentDataType.STRING, "SURVIVAL");
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "intro_return"), PersistentDataType.STRING,
                "deleted_world;7.5;64.0;7.5;0.0;0.0");
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
        assertTrue(sameBlock(player.getLocation(), spawn));
    }

    @Test
    void playersWhoPlayedBeforeAndDisabledIntroGetNothing() {
        TestPlayer veteran = new TestPlayer(server, "Veteran");
        veteran.setPlayedBefore(true);
        server.addPlayer(veteran);
        server.getScheduler().performTicks(10);
        assertEquals(GameMode.SURVIVAL, veteran.getGameMode(), "known to the server before the plugin was installed");

        setIntro(Map.of("enabled", false));
        TestPlayer newbie = joinForTheFirstTime("Newbie");
        server.getScheduler().performTicks(10);
        assertEquals(GameMode.SURVIVAL, newbie.getGameMode());
        assertTrue(newbie.openedBooks().isEmpty());
    }

    @Test
    void withoutCameraPointsANewPlayerOnlyGetsTheBook() {
        TestPlayer admin = addAdmin("Admin");
        admin.performCommand("stonespawn intro removepoint tower");
        admin.performCommand("stonespawn intro removepoint market");

        TestPlayer newbie = joinForTheFirstTime("Newbie");
        server.getScheduler().performTicks(10);

        assertEquals(GameMode.SURVIVAL, newbie.getGameMode());
        assertTrue(sameBlock(newbie.getLocation(), spawn));
        assertEquals(1, newbie.openedBooks().size());
        assertEquals("Welcome, Newbie!", plain(newbie.openedBooks().getFirst().pages().getFirst()).lines().findFirst().orElse(""));
    }
}
