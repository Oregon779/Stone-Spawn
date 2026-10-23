package dev.stonespawn.plugin.intro;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cinematic extras of the intro: boss bar, typed subtitles, time and weather, music, commands and the tools. */
class IntroPremiumTest extends PluginTestBase {

    // Two points, one second stay and one second flight each.
    private static final int WHOLE_INTRO_TICKS = 70;

    private final List<String> commandLog = new ArrayList<>();
    private Location spawn;

    @BeforeEach
    void twoPointsAndALoggingCommand() {
        spawn = spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        TestPlayer admin = addAdmin("Builder");
        admin.teleport(new Location(world, 10.5, 80, 10.5, 90f, 20f));
        admin.performCommand("stonespawn intro addpoint tower");
        admin.teleport(new Location(world, 30.5, 90, -10.5, -90f, 0f));
        admin.performCommand("stonespawn intro addpoint market");
        setIntro(Map.of("enabled", true, "fly-seconds", 1.0, "stay-seconds", 1.0, "start-delay-ticks", 5,
                "points.tower.title", "&bThe tower", "points.tower.subtitle", "&7Hi {player}",
                "points.tower.chapter", "&bChapter one", "points.market.title", "&eThe market"));
        server.getCommandMap().register("test", new Command("introlog") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                commandLog.add(String.join(" ", args));
                return true;
            }
        });
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

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<BossBar> bars(TestPlayer player) {
        List<BossBar> bars = new ArrayList<>();
        player.activeBossBars().forEach(bars::add);
        return bars;
    }

    private static long heard(TestPlayer player, String soundPart) {
        return player.getHeardSounds().stream()
                .filter(sound -> sound.getSound().toLowerCase().replace('_', '.').contains(soundPart)).count();
    }

    @Test
    void bossBarShowsTheChapterAndTheProgress() {
        TestPlayer player = addPlayer("Steve");
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(5);

        List<BossBar> shown = bars(player);
        assertEquals(1, shown.size());
        BossBar bar = shown.getFirst();
        assertEquals("Chapter one", plain(bar.name()));
        float early = bar.progress();

        server.getScheduler().performTicks(40);
        assertEquals("The market", plain(bar.name()), "a point without a chapter uses its title");
        assertTrue(bar.progress() > early);

        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);
        assertTrue(bars(player).isEmpty(), "gone when the intro ends");
    }

    @Test
    void subtitleIsTypedLetterByLetterInItsColor() {
        TestPlayer player = addPlayer("Steve");
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(15);

        assertEquals("", plain(player.shownTitles().getFirst().subtitle()), "starts empty");
        List<String> typed = player.subtitleUpdates().stream().map(IntroPremiumTest::plain).toList();
        assertEquals(List.of("H", "Hi", "Hi ", "Hi S", "Hi St", "Hi Ste", "Hi Stev", "Hi Steve"), typed);
        assertEquals(7, heard(player, "note.block.hat"), "a typing sound for every letter, not for the space");

        Component cut = Typewriter.cut(Component.text("Hello", NamedTextColor.GRAY), 2);
        assertEquals("He", plain(cut));
        assertEquals(NamedTextColor.GRAY, cut.color(), "the shown letters keep their color");
    }

    @Test
    void timeAndWeatherChangeOnlyForTheViewerAndAreResetAfterwards() {
        setIntro(Map.of("time", "noon", "points.market.time", 13000, "points.market.weather", "rain"));
        TestPlayer player = addPlayer("Steve");
        TestPlayer other = addPlayer("Other");
        plugin.getIntroManager().start(player);

        server.getScheduler().performTicks(5);
        assertEquals(6000L, player.getPlayerTime());
        assertFalse(player.isPlayerTimeRelative(), "the sky stands still at noon");

        server.getScheduler().performTicks(25);
        long during = player.getPlayerTime();
        assertTrue(during > 6000 && during < 13000, "the sky changes during the flight: " + during);

        server.getScheduler().performTicks(15);
        assertEquals(13000L, player.getPlayerTime());
        assertEquals(WeatherType.DOWNFALL, player.getPlayerWeather());
        assertEquals(0L, other.getPlayerTime(), "nobody else is affected");
        assertNull(other.getPlayerWeather());

        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);
        assertTrue(player.isPlayerTimeRelative());
        assertEquals(0L, player.getPlayerTime());
        assertNull(player.getPlayerWeather());
        assertTrue(world.getTime() < 1000, "the world's own time just runs on, it never jumped: " + world.getTime());
    }

    @Test
    void aPersonalTimeSetByAnotherPluginStays() {
        TestPlayer player = addPlayer("Steve");
        player.setPlayerTime(18000, false);
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(WHOLE_INTRO_TICKS + 5);
        assertEquals(18000L, player.getPlayerTime());
    }

    @Test
    void musicPlaysDuringTheIntroAndStopsAtTheEnd() {
        setIntro(Map.of("music.sound", "MUSIC_DISC_RELIC"));
        TestPlayer player = addPlayer("Steve");
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(2);

        assertEquals(1, heard(player, "music.disc.relic"));
        assertTrue(player.stoppedSounds().contains("MUSIC"), "the game's own music is stopped first");

        server.getScheduler().performTicks(WHOLE_INTRO_TICKS);
        assertTrue(player.stoppedSounds().stream().anyMatch(sound -> sound.toLowerCase().contains("relic")),
                "stopped at the end: " + player.stoppedSounds());
    }

    @Test
    void pointCommandsRunOnArrivalAndRewardsOnlyOnTheFirstJoin() {
        setIntro(Map.of("points.market.commands", List.of("introlog market {player}"),
                "end-commands", List.of("/introlog reward {player}")));

        TestPlayer tester = addPlayer("Tester");
        plugin.getIntroManager().start(tester);
        server.getScheduler().performTicks(WHOLE_INTRO_TICKS + 5);
        assertEquals(List.of("market Tester"), commandLog, "no reward for an intro played by command");

        commandLog.clear();
        TestPlayer newbie = new TestPlayer(server, "Newbie");
        server.addPlayer(newbie);
        server.getScheduler().performTicks(5 + WHOLE_INTRO_TICKS + 5);
        assertEquals(List.of("market Newbie", "reward Newbie"), commandLog);

        commandLog.clear();
        TestPlayer skipper = new TestPlayer(server, "Skipper");
        server.addPlayer(skipper);
        server.getScheduler().performTicks(8);
        server.getPluginManager().callEvent(new PlayerToggleSneakEvent(skipper, true));
        server.getScheduler().performTicks(2);
        assertEquals(List.of("reward Skipper"), commandLog, "skipping still gives the reward");
        assertTrue(sameBlock(newbie.getLocation(), spawn));
    }

    @Test
    void lookAtCommandAndOrbitKeepTheCameraOnTheTarget() throws IOException {
        TestPlayer admin = addAdmin("Admin");
        admin.setTargetBlock(world.getBlockAt(40, 85, 0));
        admin.performCommand("stonespawn intro lookat market");
        assertEquals(List.of("[StoneSpawn] ✔ Camera point market now keeps looking at the block at 40, 85, 0."),
                messages(admin));
        assertTrue(Files.readString(new File(plugin.getDataFolder(), "intro.yml").toPath())
                .contains("    look-at:\n      x: 40.5\n      y: 85.5\n      z: 0.5\n"));

        setIntro(Map.of("points.market.orbit-degrees", 180, "points.market.stay-seconds", 2.0));
        Vector target = new Vector(40.5, 85.5, 0.5);
        TestPlayer player = addPlayer("Steve");
        plugin.getIntroManager().start(player);
        server.getScheduler().performTicks(45);

        Entity camera = player.getSpectatorTarget();
        double radius = camera.getLocation().toVector().distance(target);
        for (int i = 0; i < 4; i++) {
            server.getScheduler().performTicks(8);
            Location now = camera.getLocation();
            assertEquals(radius, now.toVector().distance(target), 1.0E-6, "circles around the target");
            Vector towards = target.clone().subtract(now.toVector()).normalize();
            assertEquals(1.0, now.getDirection().dot(towards), 1.0E-3, "always looks at it");
        }

        admin.performCommand("stonespawn intro lookat market clear");
        admin.setTargetBlock(null);
        admin.performCommand("stonespawn intro lookat market");
        assertEquals(List.of("[StoneSpawn] ✔ Camera point market looks in its saved direction again.",
                "[StoneSpawn] ✖ Look at a block (up to 256 blocks away) to choose what the camera looks at."), messages(admin));
        assertFalse(Files.readString(new File(plugin.getDataFolder(), "intro.yml").toPath()).contains("look-at:\n      x"));
    }

    @Test
    void previewShowsThePathOnlyToTheAdminUntilHidden() {
        TestPlayer admin = addAdmin("Admin");
        TestPlayer bystander = addPlayer("Bystander");
        admin.performCommand("stonespawn intro preview");
        server.getScheduler().performTicks(1);

        assertTrue(admin.receivedParticles().stream().filter(particle -> particle == Particle.DUST).count() > 20,
                "the path is drawn");
        assertTrue(bystander.receivedParticles().isEmpty());

        admin.performCommand("stonespawn intro preview");
        int drawn = admin.receivedParticles().size();
        server.getScheduler().performTicks(40);
        assertEquals(drawn, admin.receivedParticles().size(), "hidden again");
        assertTrue(messages(admin).getLast().contains("Camera path hidden"));
    }

    @Test
    void addPointSaysSoWhenIntroYmlCanNotBeSaved() throws IOException {
        Files.writeString(new File(plugin.getDataFolder(), "intro.yml").toPath(), "points:\n\t broken: [\n");
        TestPlayer admin = addAdmin("Admin");
        admin.performCommand("stonespawn intro addpoint tower");
        assertEquals(List.of("[StoneSpawn] ✖ intro.yml could not be saved - see the console for why."), messages(admin));
    }
}
