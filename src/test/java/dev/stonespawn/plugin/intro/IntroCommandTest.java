package dev.stonespawn.plugin.intro;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntroCommandTest extends PluginTestBase {

    private String introFile() throws IOException {
        return Files.readString(new File(plugin.getDataFolder(), "intro.yml").toPath());
    }

    private void writeIntroFile(String content) throws IOException {
        Files.writeString(new File(plugin.getDataFolder(), "intro.yml").toPath(), content);
        plugin.reload();
    }

    @Test
    void pointsAreWrittenWithoutTouchingTheRestOfTheFile() throws IOException {
        String fresh = introFile();
        TestPlayer admin = addAdmin("Admin");
        admin.teleport(new Location(world, 10.5, 80, -3.25, 450f, 20f));

        admin.performCommand("stonespawn intro addpoint Tower");
        double eyeY = admin.getEyeLocation().getY();
        String withTower = fresh.replace("points: {}\n", """
                points:
                  tower:
                    world: "world"
                    x: 10.5
                    y: %s
                    z: -3.25
                    yaw: 90.0
                    pitch: 20.0
                """.formatted(eyeY));
        assertEquals(withTower, introFile(), "only the new point is added, yaw is normalized");

        // The admin writes texts for the point by hand, in their own style.
        String view = "    yaw: 90.0\n    pitch: 20.0\n";
        String withTitle = withTower.replace(view, view + "    title: \"&b&lThe tower\"   # nice\n");
        writeIntroFile(withTitle);

        admin.teleport(new Location(world, 12.5, 80, -3.25, 0f, 0f));
        admin.performCommand("stonespawn intro addpoint tower");
        assertEquals(withTitle.replace("    x: 10.5\n", "    x: 12.5\n").replace(view, "    yaw: 0.0\n    pitch: 0.0\n"),
                introFile(), "moving a point keeps its texts");

        admin.performCommand("stonespawn intro removepoint tower");
        assertEquals(fresh, introFile(), "removing the last point leaves points: {} as before");

        assertEquals(List.of(
                "[StoneSpawn] ✔ Camera point tower added at your position and view.",
                "[StoneSpawn] ✔ Camera point tower moved to your position and view.",
                "[StoneSpawn] ✔ Camera point tower removed."), messages(admin));
    }

    @Test
    void listPlayAndErrors() {
        TestPlayer admin = addAdmin("Admin");
        admin.performCommand("stonespawn intro list");
        admin.performCommand("stonespawn intro play");
        admin.performCommand("stonespawn intro removepoint nowhere");
        admin.performCommand("stonespawn intro addpoint a.b");
        admin.performCommand("stonespawn intro play Nobody");
        admin.performCommand("stonespawn intro");

        assertEquals(List.of(
                "[StoneSpawn] The intro has no camera points yet.",
                "[StoneSpawn] ✖ The intro has no camera points yet. Add one with /stonespawn intro addpoint <name>.",
                "[StoneSpawn] ✖ There is no camera point called nowhere.",
                "[StoneSpawn] ✖ a.b is not a valid name. Allowed are a-z, 0-9, _ and - (max. 32 characters).",
                "[StoneSpawn] ✖ Player Nobody not found.",
                "[StoneSpawn] ✖ Usage: /stonespawn intro <play [player]|addpoint <name>|removepoint <name>|list>"),
                messages(admin));

        admin.teleport(new Location(world, 1.5, 70, 2.5));
        admin.performCommand("stonespawn intro addpoint first");
        admin.teleport(new Location(world, -4.5, 75, 8.5));
        admin.performCommand("stonespawn intro addpoint second");
        messages(admin);
        admin.performCommand("stonespawn intro list");
        assertEquals(List.of(
                "[StoneSpawn] Intro camera points, in order:",
                "1. first - world 1.5, " + oneDecimal(admin, 70) + ", 2.5",
                "2. second - world -4.5, " + oneDecimal(admin, 75) + ", 8.5"), messages(admin));

        TestPlayer player = addPlayer("Steve");
        admin.performCommand("stonespawn intro play Steve");
        admin.performCommand("stonespawn intro play Steve");
        assertEquals(List.of("[StoneSpawn] ✔ Playing the intro for Steve.",
                "[StoneSpawn] ✖ Steve is already watching the intro."), messages(admin));
        assertEquals(GameMode.SPECTATOR, player.getGameMode());
    }

    private static String oneDecimal(TestPlayer player, double feetY) {
        return String.format(java.util.Locale.ROOT, "%.1f", feetY + player.getEyeHeight());
    }

    @Test
    void onlyAdminsCanUseItAndTabCompletionHelps() {
        TestPlayer player = addPlayer("Steve");
        player.performCommand("stonespawn intro play");
        assertEquals(List.of("[StoneSpawn] ✖ You don't have permission to do this."), messages(player));
        assertEquals(List.of(), plugin.getCommand("stonespawn").tabComplete(player, "stonespawn", new String[]{"intro", ""}));

        TestPlayer admin = addAdmin("Admin");
        admin.performCommand("stonespawn intro addpoint tower");
        assertEquals(List.of("play", "addpoint", "removepoint", "list"),
                plugin.getCommand("stonespawn").tabComplete(admin, "stonespawn", new String[]{"intro", ""}));
        assertEquals(List.of("tower"),
                plugin.getCommand("stonespawn").tabComplete(admin, "stonespawn", new String[]{"intro", "removepoint", "t"}));
        assertTrue(plugin.getCommand("stonespawn").tabComplete(admin, "stonespawn", new String[]{"intro", "play", "St"})
                .contains("Steve"));
    }
}
