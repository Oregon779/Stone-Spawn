package dev.stonespawn.plugin.config;

import dev.stonespawn.plugin.PluginTestBase;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The update as a server owner experiences it: real plugin files, changed by hand, then a restart or reload. */
class ConfigFileUpdateTest extends PluginTestBase {

    private String read(String name) throws IOException {
        return Files.readString(new File(plugin.getDataFolder(), name).toPath());
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(new File(plugin.getDataFolder(), name).toPath(), content);
    }

    private static String withoutLines(String text, String first, String last) {
        int start = text.indexOf(first);
        int end = text.indexOf(last, start) + last.length();
        assertTrue(start >= 0 && end > start, first);
        return text.substring(0, start) + text.substring(end);
    }

    @Test
    void outdatedConfigIsCompletedBeforeItsValuesAreRead() throws IOException {
        String fresh = read("config.yml");
        String old = withoutLines(fresh, "  # Admins can create extra spawns", "  spawn-gui: true\n")
                .replace("delay-seconds: 3", "delay-seconds: 1");
        write("config.yml", old);

        plugin.reload();

        assertEquals(fresh.replace("delay-seconds: 3", "delay-seconds: 1"), read("config.yml"));
        assertTrue(plugin.getConfigManager().getBoolean("command.spawn-gui", false),
                "the added option is read with its bundled default right away");
        assertEquals(1, plugin.getConfigManager().getInt("teleport.delay-seconds", -1));
    }

    @Test
    void configWithASyntaxErrorIsNeverOverwritten() throws IOException {
        String broken = read("config.yml")
                .replace("delay-seconds: 3", "delay-seconds: 7")
                .replace("cooldown-seconds: 10", "cooldown-seconds: 99\n\t broken: [");
        write("config.yml", broken);

        plugin.reload();
        assertEquals(broken, read("config.yml"), "the owner's settings must survive the typo");
        assertEquals(3, plugin.getConfigManager().getInt("teleport.delay-seconds", -1), "defaults until it is fixed");

        write("config.yml", broken.replace("\n\t broken: [", ""));
        plugin.reload();
        assertEquals(7, plugin.getConfigManager().getInt("teleport.delay-seconds", -1));
        assertEquals(99, plugin.getConfigManager().getInt("command.cooldown-seconds", -1));
    }

    @Test
    void outdatedLanguageFileGetsNewMessagesAndKeepsChangedOnes() throws IOException {
        String fresh = read("languages/de/messages.yml");
        String changed = fresh.replace("Konfiguration und Nachrichten wurden neu geladen.", "Neu geladen!");
        write("languages/de/messages.yml", withoutLines(changed, "  # {spawn}\n  success-named:", "gesetzt.\"\n"));

        configure(Map.of("language", "de"));

        assertEquals(changed, read("languages/de/messages.yml"));
        assertEquals("&a✔ Neu geladen!", plugin.getMessageManager().getRaw("general.reload-success"));
    }

    @Test
    void guiEntriesOfTheOwnerAreKept() throws IOException {
        String withOwnItems = read("gui.yml").replace("items: {}", """
                items:
                  close:
                    slot: 22
                    material: BARRIER
                    action: CLOSE""");
        write("gui.yml", withoutLines(withOwnItems, "\n# Leave a sound empty", "  pitch: 1.0\n"));

        plugin.reload();

        assertEquals(withOwnItems, read("gui.yml"));
    }

    @Test
    void migrationKeepsABackupOfThePreviousFile() throws IOException {
        File file = new File(plugin.getDataFolder(), "update-test.yml");
        String v1 = "teleport:\n  delay: 7\n";
        Files.writeString(file.toPath(), v1);
        ConfigSchema schema = new ConfigSchema(List.of(
                ConfigMigration.to(2).move("teleport.delay", "teleport.delay-seconds")), Set.of());

        YamlConfiguration config = ConfigUpdater.load(plugin, "update-test/config.yml", file, schema);

        assertEquals(7, config.getInt("teleport.delay-seconds"));
        assertEquals(2, YamlConfiguration.loadConfiguration(file).getInt(ConfigSchema.VERSION_KEY));
        File[] backups = plugin.getDataFolder().listFiles(
                (dir, name) -> name.startsWith("update-test.yml.v1-") && name.endsWith(".bak"));
        assertNotNull(backups);
        assertEquals(1, backups.length);
        assertEquals(v1, Files.readString(backups[0].toPath()));
    }

    @Test
    void missingFileIsCreatedFromTheBundledOne() throws IOException {
        File file = new File(plugin.getDataFolder(), "config.yml");
        String bundled = read("config.yml");
        assertTrue(file.delete());

        plugin.reload();

        assertEquals(bundled, read("config.yml"));
    }
}
