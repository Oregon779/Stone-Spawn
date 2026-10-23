package dev.stonespawn.plugin.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    private static final ConfigSchema PLAIN = new ConfigSchema(List.of(), Set.of());

    private static YamlConfiguration yaml(String content) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        return yaml;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigUpdaterTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Removes the lines from {@code first} up to and including {@code last}, both given by their text. */
    private static String withoutLines(String text, String first, String last) {
        int start = text.indexOf(first);
        int end = text.indexOf(last, start) + last.length();
        assertTrue(start >= 0 && end > start, first);
        return text.substring(0, start) + text.substring(end);
    }

    // ---------------------------------------------------------------- adding options

    @Test
    void newNestedOptionIsAddedAndChangedValuesStay() {
        String defaults = """
                # My plugin
                language: en

                teleport:
                  # Delay in seconds.
                  delay-seconds: 3
                  # Cancel when moving.
                  cancel-on-move: true

                features:
                  example:
                    # Turns the example on.
                    enabled: false
                """;
        String old = """
                # My plugin
                language: de

                teleport:
                  # Delay in seconds.
                  delay-seconds: 10   # my own note
                """;

        ConfigUpdater.Result result = ConfigUpdater.update(old, defaults, PLAIN);

        assertEquals("""
                # My plugin
                language: de

                teleport:
                  # Delay in seconds.
                  delay-seconds: 10   # my own note
                  # Cancel when moving.
                  cancel-on-move: true

                features:
                  example:
                    # Turns the example on.
                    enabled: false
                """, result.text());
        assertEquals(2, result.addedKeys());
        assertEquals("de", result.config().getString("language"));
        assertEquals(10, result.config().getInt("teleport.delay-seconds"));
        assertFalse(result.config().getBoolean("features.example.enabled", true));
    }

    @Test
    void ownFormattingAndCommentsStayExactlyAsWritten() {
        String defaults = """
                title: "&8Spawns"
                slots: [10, 11, 12]
                sound: UI_BUTTON_CLICK
                lore:
                  - "&7Line"
                volume: 0.6
                """;
        String old = """
                # I changed the title!
                title: "&cMy very long custom title that would normally be wrapped by the YAML writer at eighty"
                slots: [1, 2, 3, 4]
                lore:
                - '&eMine'

                volume: 1.0
                """;

        String text = ConfigUpdater.update(old, defaults, PLAIN).text();

        assertEquals("""
                # I changed the title!
                title: "&cMy very long custom title that would normally be wrapped by the YAML writer at eighty"
                slots: [1, 2, 3, 4]
                sound: UI_BUTTON_CLICK
                lore:
                - '&eMine'

                volume: 1.0
                """, text);
    }

    @Test
    void optionThatIsFirstInItsSectionIsAddedOnTop() {
        String defaults = """
                teleport:
                  # Delay in seconds.
                  delay-seconds: 3
                  # Cancel when moving.
                  cancel-on-move: true
                """;
        String old = """
                teleport:
                  # Cancel when moving.
                  cancel-on-move: false
                """;
        assertEquals(defaults.replace("cancel-on-move: true", "cancel-on-move: false"),
                ConfigUpdater.update(old, defaults, PLAIN).text());
    }

    @Test
    void indentationOfTheFileIsFollowed() {
        String defaults = """
                elytra:
                  enabled: false
                  boost:
                    enabled: true
                    trigger: SWAP_HAND
                """;
        String old = """
                elytra:
                    enabled: true
                    boost:
                        enabled: false
                """;
        assertEquals("""
                elytra:
                    enabled: true
                    boost:
                        enabled: false
                        trigger: SWAP_HAND
                """, ConfigUpdater.update(old, defaults, PLAIN).text());
    }

    @Test
    void windowsLineEndingsAreKept() {
        String defaults = "a: 1\nb: 2\n";
        String text = ConfigUpdater.update("a: 5\r\n", defaults, PLAIN).text();
        assertEquals("a: 5\r\nb: 2\r\n", text);
    }

    @Test
    void outdatedBundledConfigBecomesIdenticalToTheCurrentOne() throws Exception {
        String bundled = resource("/config.yml");
        String old = bundled;
        old = withoutLines(old, "\n# Players who jump or fall off", "    particle-count: 20\n");
        old = withoutLines(old, "  # Admins can create extra spawns", "  spawn-gui: true\n");
        old = withoutLines(old, "  # Delay in seconds before a manual", "  delay-seconds: 3\n");
        old = withoutLines(old, "    # If true, a respawn anchor", "    ignore-respawn-anchor: true\n");
        old = withoutLines(old, "    particle-count-per-point: 1\n\n# Settings", "    particle-count-per-point: 1\n");
        old = withoutLines(old, "\n# Layout version of this file", "config-version: 1\n");
        // Settings the server owner changed, partly written differently than in the bundled file.
        old = old.replace("language: en", "language: de")
                .replace("  cancel-on-move: true", "  cancel-on-move: false")
                .replace("chat: \"&b➜ &eTeleporting to spawn in &b{seconds}&e...\"", "chat: '&cMy own text'");

        ConfigUpdater.Result result = ConfigUpdater.update(old, bundled, ConfigSchema.CONFIG);

        String expected = bundled.replace("language: en", "language: de")
                .replace("  cancel-on-move: true", "  cancel-on-move: false")
                .replace("chat: \"&b➜ &eTeleporting to spawn in &b{seconds}&e...\"", "chat: '&cMy own text'");
        assertEquals(expected, result.text());
        assertEquals(6, result.addedKeys());
        assertEquals("&cMy own text", result.config().getString("teleport.countdown.messages.chat"));
        assertNull(ConfigUpdater.update(result.text(), bundled, ConfigSchema.CONFIG).text(),
                "a second start has nothing left to do");
    }

    @Test
    void completeFileIsNotRewritten() throws Exception {
        String bundled = resource("/config.yml");
        String changed = bundled.replace("delay-seconds: 3", "delay-seconds: 8");
        ConfigUpdater.Result result = ConfigUpdater.update(changed, bundled, ConfigSchema.CONFIG);
        assertNull(result.text());
        assertEquals(8, result.config().getInt("teleport.delay-seconds"));
    }

    @Test
    void userValueOfDifferentTypeIsKept() {
        String defaults = "spawn:\n  join:\n    mode: FIRST_JOIN\n";
        ConfigUpdater.Result result = ConfigUpdater.update("spawn:\n  join: ALWAYS\n", defaults, PLAIN);
        assertNull(result.text());
        assertEquals("ALWAYS", result.config().getString("spawn.join"));
    }

    @Test
    void userSectionsNeverGetBundledExamples() {
        String defaults = """
                rows: 3
                items:
                  close:
                    slot: 22
                """;
        ConfigSchema gui = new ConfigSchema(List.of(), Set.of("items"));

        assertNull(ConfigUpdater.update("rows: 3\nitems: {}\n", defaults, gui).text(), "emptied on purpose");
        assertNull(ConfigUpdater.update("rows: 3\nitems:\n  deco:\n    slot: 1\n", defaults, gui).text());
        assertEquals(22, ConfigUpdater.update("rows: 3\n", defaults, gui).config().getInt("items.close.slot"),
                "a missing section is still created with its examples");
    }

    @Test
    void sectionWrittenInFlowStyleFallsBackToARegularMerge() {
        String defaults = """
                boost:
                  enabled: true
                  # Which key.
                  trigger: SWAP_HAND
                """;
        ConfigUpdater.Result result = ConfigUpdater.update("boost: {enabled: false}\n", defaults, PLAIN);

        assertNotNull(result.text());
        assertTrue(result.text().contains("# Which key."), result.text());
        YamlConfiguration written = assertDoesNotThrowParse(result.text());
        assertFalse(written.getBoolean("boost.enabled"));
        assertEquals("SWAP_HAND", written.getString("boost.trigger"));
    }

    @Test
    void mergeFallbackDoesNotWrapLongTexts() {
        String longText = "x".repeat(150) + " " + "y".repeat(150);
        String defaults = "a: {b: 1, c: 2}\n";
        String text = ConfigUpdater.update("a: {b: 5}\nlong: " + longText + "\n", defaults, PLAIN).text();
        assertTrue(text.contains("long: " + longText + "\n"), text);
    }

    // ---------------------------------------------------------------- unreadable files

    @Test
    void brokenFileIsNeverOverwritten() {
        String defaults = "delay-seconds: 3\ncooldown-seconds: 10\n";
        String broken = "delay-seconds: 7\ncooldown-seconds: 99\n\t broken: [\n";

        ConfigUpdater.Result result = ConfigUpdater.update(broken, defaults, PLAIN);

        assertNull(result.text(), "the file must stay as it is");
        assertNotNull(result.error());
        assertEquals(3, result.config().getInt("delay-seconds"), "defaults are used until the file is fixed");
    }

    // ---------------------------------------------------------------- versioned migrations

    private static final String DEFAULTS_V2 = """
            teleport:
              # Seconds before teleporting.
              delay-seconds: 3
              cancel-on-move: true

            # Do not change it.
            config-version: 2
            """;

    private static final ConfigSchema RENAME_TO_V2 = new ConfigSchema(List.of(
            ConfigMigration.to(2).move("teleport.delay", "teleport.delay-seconds").remove("old-option")
    ), Set.of());

    @Test
    void renamedOptionKeepsTheOwnersValue() {
        String v1 = """
                teleport:
                  # Seconds before teleporting.
                  delay: 7
                old-option: true
                """;

        ConfigUpdater.Result result = ConfigUpdater.update(v1, DEFAULTS_V2, RENAME_TO_V2);
        YamlConfiguration written = assertDoesNotThrowParse(result.text());

        assertTrue(result.migrated());
        assertEquals(1, result.fromVersion());
        assertEquals(2, result.toVersion());
        assertEquals(7, written.getInt("teleport.delay-seconds"));
        assertFalse(written.contains("teleport.delay"));
        assertFalse(written.contains("old-option"));
        assertTrue(written.getBoolean("teleport.cancel-on-move"), "new options are still added");
        assertEquals(2, written.getInt(ConfigSchema.VERSION_KEY));
        assertEquals(List.of("Seconds before teleporting."), written.getComments("teleport.delay-seconds"));
        assertNull(ConfigUpdater.update(result.text(), DEFAULTS_V2, RENAME_TO_V2).text(), "migrates only once");
    }

    @Test
    void migrationNeverOverwritesAnExistingValue() {
        String v1 = "teleport:\n  delay: 7\n  delay-seconds: 4\n";
        YamlConfiguration written = assertDoesNotThrowParse(ConfigUpdater.update(v1, DEFAULTS_V2, RENAME_TO_V2).text());
        assertEquals(4, written.getInt("teleport.delay-seconds"));
        assertEquals(7, written.getInt("teleport.delay"), "nothing is deleted that wasn't moved");
    }

    @Test
    void stepsRunInOrderAcrossSeveralVersions() {
        String defaultsV3 = "c: 0\nconfig-version: 3\n";
        ConfigSchema schema = new ConfigSchema(List.of(
                ConfigMigration.to(2).move("a", "b"),
                ConfigMigration.to(3).move("b", "c")
        ), Set.of());

        ConfigUpdater.Result result = ConfigUpdater.update("a: 42\n", defaultsV3, schema);
        YamlConfiguration written = assertDoesNotThrowParse(result.text());

        assertEquals(42, written.getInt("c"));
        assertFalse(written.contains("a") || written.contains("b"));
        assertEquals(3, written.getInt(ConfigSchema.VERSION_KEY));
    }

    @Test
    void customStepCanConvertAValue() {
        String defaults = "delay-ticks: 60\nconfig-version: 2\n";
        ConfigSchema schema = new ConfigSchema(List.of(
                ConfigMigration.to(2).custom(config -> {
                    if (config.isInt("delay-seconds")) {
                        config.set("delay-ticks", config.getInt("delay-seconds") * 20);
                        config.set("delay-seconds", null);
                    }
                })
        ), Set.of());
        YamlConfiguration written = assertDoesNotThrowParse(
                ConfigUpdater.update("delay-seconds: 5\n", defaults, schema).text());
        assertEquals(100, written.getInt("delay-ticks"));
    }

    @Test
    void fileFromANewerVersionIsLeftAlone() {
        String newer = "teleport:\n  delay-seconds: 9\nconfig-version: 3\n";
        ConfigUpdater.Result result = ConfigUpdater.update(newer, DEFAULTS_V2, RENAME_TO_V2);

        assertNull(result.text());
        assertEquals(3, result.fromVersion());
        assertEquals(9, result.config().getInt("teleport.delay-seconds"));
        assertTrue(result.config().getBoolean("teleport.cancel-on-move"), "gaps are filled in memory only");
    }

    @Test
    void migrationVersionsMustAscend() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigSchema(List.of(
                ConfigMigration.to(3), ConfigMigration.to(2)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConfigSchema(List.of(
                ConfigMigration.to(1)), Set.of()));
    }

    // ---------------------------------------------------------------- bundled files

    @Test
    void bundledFilesMatchTheirSchemas() throws Exception {
        Map<String, ConfigSchema> files = Map.of(
                "/config.yml", ConfigSchema.CONFIG,
                "/gui.yml", ConfigSchema.GUI,
                "/languages/en/messages.yml", ConfigSchema.MESSAGES,
                "/languages/de/messages.yml", ConfigSchema.MESSAGES);

        for (Map.Entry<String, ConfigSchema> file : files.entrySet()) {
            String text = resource(file.getKey());
            YamlConfiguration bundled = yaml(text);
            ConfigSchema schema = file.getValue();

            assertEquals(schema.latestVersion(), bundled.getInt(ConfigSchema.VERSION_KEY, 1),
                    file.getKey() + ": config-version must match the last migration in ConfigSchema");
            for (String section : schema.userSections()) {
                assertTrue(bundled.isConfigurationSection(section), file.getKey() + " has no section " + section);
            }
            assertNull(ConfigUpdater.update(text, text, schema).text(), file.getKey());
        }
    }

    /**
     * Files rewritten by earlier versions look different from the bundled ones (Bukkit re-quotes strings and
     * indents blank lines). Taking out any single option of any bundled file must bring it back as text,
     * with every existing line kept as it was.
     */
    @Test
    void everyOptionOfEveryBundledFileCanBeAddedToARewrittenFile() throws Exception {
        Map<String, ConfigSchema> files = Map.of(
                "/config.yml", ConfigSchema.CONFIG,
                "/gui.yml", ConfigSchema.GUI,
                "/languages/en/messages.yml", ConfigSchema.MESSAGES,
                "/languages/de/messages.yml", ConfigSchema.MESSAGES);

        for (Map.Entry<String, ConfigSchema> file : files.entrySet()) {
            String bundled = resource(file.getKey());
            YamlConfiguration full = yaml(bundled);
            for (String option : full.getKeys(true)) {
                YamlConfiguration old = yaml(bundled);
                old.set(option, null);
                String oldText = old.saveToString();

                String where = file.getKey() + " without " + option;
                if (leavesEmptySection(old, option)) {
                    // Saved as "section: {}", which can't be extended line by line: the regular merge takes over.
                    YamlConfiguration merged = ConfigUpdater.update(oldText, bundled, file.getValue()).config();
                    assertEquals(full.getValues(true).toString(), merged.getValues(true).toString(), where);
                    continue;
                }
                String patched = YamlTextPatcher.insertMissing(oldText, bundled, file.getValue().userSections());

                assertNotNull(patched, where);
                assertEquals(full.getValues(true).toString(), yaml(patched).getValues(true).toString(), where);
                assertTrue(keepsEveryLine(oldText, patched), where + ":\n" + patched);
            }
        }
    }

    private static boolean leavesEmptySection(YamlConfiguration config, String removedOption) {
        int dot = removedOption.lastIndexOf('.');
        return dot > 0 && config.isConfigurationSection(removedOption.substring(0, dot))
                && config.getConfigurationSection(removedOption.substring(0, dot)).getKeys(false).isEmpty();
    }

    private static boolean keepsEveryLine(String before, String after) {
        List<String> remaining = List.of(after.split("\n", -1));
        int index = 0;
        for (String line : before.split("\n", -1)) {
            while (index < remaining.size() && !remaining.get(index).equals(line)) {
                index++;
            }
            if (index++ >= remaining.size()) {
                return false;
            }
        }
        return true;
    }

    private static YamlConfiguration assertDoesNotThrowParse(String text) {
        assertNotNull(text);
        try {
            return yaml(text);
        } catch (InvalidConfigurationException ex) {
            throw new AssertionError("Written file is not valid YAML:\n" + text, ex);
        }
    }
}
