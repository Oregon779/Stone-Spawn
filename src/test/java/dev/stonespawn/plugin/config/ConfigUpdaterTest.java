package dev.stonespawn.plugin.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    private static YamlConfiguration yaml(String content) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        return yaml;
    }

    @Test
    void addsMissingKeysWithCommentsAndKeepsUserValues() throws Exception {
        YamlConfiguration defaults = yaml("""
                command:
                  cooldown-seconds: 10
                  # Opens the GUI.
                  spawn-gui: true

                # Gliding off the island.
                elytra:
                  enabled: false
                  boost:
                    # SWAP_HAND, SNEAK or LEFT_CLICK
                    trigger: SWAP_HAND
                """);
        YamlConfiguration current = yaml("""
                command:
                  cooldown-seconds: 42
                """);

        int added = ConfigUpdater.mergeSection(defaults, current);
        String saved = current.saveToString();

        assertEquals(2, added);
        assertEquals(42, current.getInt("command.cooldown-seconds"));
        assertTrue(current.getBoolean("command.spawn-gui"));
        assertEquals("SWAP_HAND", current.getString("elytra.boost.trigger"));
        assertTrue(saved.contains("# Opens the GUI."), saved);
        assertTrue(saved.contains("# Gliding off the island."), saved);
        assertTrue(saved.contains("# SWAP_HAND, SNEAK or LEFT_CLICK"), saved);
    }

    @Test
    void doesNotReplaceUserValueOfDifferentType() throws Exception {
        YamlConfiguration defaults = yaml("spawn:\n  join:\n    mode: FIRST_JOIN\n");
        YamlConfiguration current = yaml("spawn:\n  join: ALWAYS\n");
        assertEquals(0, ConfigUpdater.mergeSection(defaults, current));
        assertEquals("ALWAYS", current.getString("spawn.join"));
    }
}
