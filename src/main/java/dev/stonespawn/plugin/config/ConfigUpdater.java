package dev.stonespawn.plugin.config;

import dev.stonespawn.plugin.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static UpdateResult update(JavaPlugin plugin, String resourcePath, File targetFile) throws IOException {
        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(targetFile);

        try (InputStream defaultStream = plugin.getResource(resourcePath)) {
            if (defaultStream == null) {
                return new UpdateResult(false, 0);
            }
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

            int added = mergeSection(defaultConfig, currentConfig);

            if (added > 0) {
                AtomicFiles.writeString(targetFile.toPath(), currentConfig.saveToString());
            }
            return new UpdateResult(added > 0, added);
        }
    }

    static int mergeSection(ConfigurationSection defaults, ConfigurationSection current) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);

            if (!current.contains(key, true)) {
                current.set(key, defaultValue);
                copyComments(defaults, current, key);
                added++;
                continue;
            }

            if (defaultValue instanceof ConfigurationSection defaultSection) {
                if (current.isConfigurationSection(key)) {
                    added += mergeSection(defaultSection, current.getConfigurationSection(key));
                }
            }
        }
        return added;
    }

    private static void copyComments(ConfigurationSection defaults, ConfigurationSection current, String key) {
        current.setComments(key, defaults.getComments(key));
        current.setInlineComments(key, defaults.getInlineComments(key));
        if (defaults.get(key) instanceof ConfigurationSection defaultSection
                && current.get(key) instanceof ConfigurationSection currentSection
                && currentSection != defaultSection) {
            for (String child : defaultSection.getKeys(false)) {
                copyComments(defaultSection, currentSection, child);
            }
        }
    }

    public record UpdateResult(boolean changed, int addedKeys) {
    }
}
