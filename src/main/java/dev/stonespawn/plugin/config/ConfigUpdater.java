package dev.stonespawn.plugin.config;

import dev.stonespawn.plugin.util.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static dev.stonespawn.plugin.config.ConfigSchema.VERSION_KEY;

/**
 * Keeps the plugin's YAML files up to date across plugin updates, before their values are read: runs the
 * migrations of {@link ConfigSchema}, adds every option that is new in the bundled file and never overwrites
 * a value the server owner set - nor a file that can't be read.
 */
public final class ConfigUpdater {

    // Long texts are never re-wrapped when a file has to be written through Bukkit.
    private static final int LINE_WIDTH = 1000;

    private ConfigUpdater() {
    }

    /**
     * Brings {@code file} up to date with the bundled {@code resourcePath} and returns its content. Creates the
     * file if it doesn't exist yet. Files that aren't bundled (e.g. a language added by the server owner) are
     * only loaded.
     */
    public static YamlConfiguration load(JavaPlugin plugin, String resourcePath, File file, ConfigSchema schema) {
        Logger logger = plugin.getLogger();
        String defaultsText;
        try {
            defaultsText = readResource(plugin, resourcePath);
        } catch (IOException ex) {
            logger.warning("Could not read the bundled " + resourcePath + ": " + ex.getMessage());
            return YamlConfiguration.loadConfiguration(file);
        }
        if (defaultsText == null) {
            return YamlConfiguration.loadConfiguration(file);
        }

        Path path = file.toPath();
        Result result;
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                AtomicFiles.writeString(path, defaultsText);
                return bundled(defaultsText);
            }
            result = update(Files.readString(path), defaultsText, schema);
        } catch (CharacterCodingException ex) {
            logger.warning(resourcePath + " is not saved as UTF-8, so new options could not be added to it safely."
                    + " Save it with UTF-8 encoding to get them.");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            mergeSection(bundled(defaultsText), config, schema.userSections());
            return config;
        } catch (IOException ex) {
            logger.warning("Could not update " + resourcePath + ": " + ex.getMessage());
            return YamlConfiguration.loadConfiguration(file);
        }

        if (result.error() != null) {
            logger.severe(resourcePath + " contains an error and was left unchanged. StoneSpawn uses the default"
                    + " values of this file until it is fixed (then run /stonespawn reload):"
                    + System.lineSeparator() + result.error());
            return result.config();
        }
        if (result.fromVersion() > result.toVersion()) {
            logger.warning(resourcePath + " was written by a newer StoneSpawn version (" + VERSION_KEY + " "
                    + result.fromVersion() + ", this version knows " + result.toVersion() + ") and was left unchanged.");
        }
        if (result.text() != null) {
            try {
                Path backup = null;
                if (result.migrated()) {
                    backup = path.resolveSibling(file.getName() + ".v" + result.fromVersion() + "-"
                            + System.currentTimeMillis() + ".bak");
                    Files.copy(path, backup);
                }
                AtomicFiles.writeString(path, result.text());
                if (backup != null) {
                    logger.info("Updated " + resourcePath + " from " + VERSION_KEY + " " + result.fromVersion() + " to "
                            + result.toVersion() + ", the previous file was saved as " + backup.getFileName());
                }
                if (result.addedKeys() > 0) {
                    logger.info("Added " + result.addedKeys() + " new option(s) to " + resourcePath);
                }
            } catch (IOException ex) {
                logger.warning("Could not save the updated " + resourcePath + ": " + ex.getMessage());
            }
        }
        return result.config();
    }

    /**
     * Works out the up-to-date content of a file without touching the disk.
     *
     * <ul>
     *   <li>Unreadable YAML: the file stays as it is, the bundled defaults are used.</li>
     *   <li>Written by a newer version: the file stays as it is, gaps are filled in memory only.</li>
     *   <li>Older {@value ConfigSchema#VERSION_KEY}: migrations run, then the file is written through Bukkit,
     *       which keeps values and comments but normalizes quotes and list style.</li>
     *   <li>Otherwise only missing options are added, as text, so the rest of the file stays untouched.</li>
     * </ul>
     */
    static Result update(String currentText, String defaultsText, ConfigSchema schema) {
        YamlConfiguration defaults = bundled(defaultsText);
        YamlConfiguration current;
        try {
            current = parse(currentText);
        } catch (InvalidConfigurationException ex) {
            return new Result(defaults, null, 0, 0, 0, ex.getMessage());
        }

        boolean versioned = defaults.isInt(VERSION_KEY);
        int latest = versioned ? defaults.getInt(VERSION_KEY) : 0;
        int version = !versioned ? 0 : current.isInt(VERSION_KEY) ? current.getInt(VERSION_KEY) : 1;

        if (version > latest) {
            mergeSection(defaults, current, schema.userSections());
            return new Result(current, null, 0, version, latest, null);
        }
        if (version < latest) {
            for (ConfigMigration migration : schema.migrations()) {
                if (migration.version() > version && migration.version() <= latest) {
                    migration.apply(current);
                }
            }
            int added = mergeSection(defaults, current, schema.userSections());
            current.set(VERSION_KEY, latest);
            return new Result(current, current.saveToString(), added, version, latest, null);
        }

        int added = mergeSection(defaults, current, schema.userSections());
        if (added == 0) {
            return new Result(current, null, 0, version, latest, null);
        }
        String patched = YamlTextPatcher.insertMissing(currentText, defaultsText, schema.userSections());
        YamlConfiguration patchedConfig = patched == null ? null : readsAs(patched, current);
        if (patchedConfig != null) {
            return new Result(patchedConfig, patched, added, version, latest, null);
        }
        return new Result(current, current.saveToString(), added, version, latest, null);
    }

    /**
     * Renames {@code parentPath.oldKey} to {@code parentPath.newKey} in a YAML file the server owner edits (e.g. a
     * spawn's own look in gui.yml). Only the key itself changes; comments and formatting stay as they are.
     *
     * @return false if the file has no such entry or can't be read, true once the entry carries the new name
     */
    public static boolean renameKey(JavaPlugin plugin, File file, String parentPath, String oldKey, String newKey) {
        String path = parentPath.isEmpty() ? oldKey : parentPath + "." + oldKey;
        String newPath = parentPath.isEmpty() ? newKey : parentPath + "." + newKey;
        return edit(plugin, file, "rename " + path, config -> {
            if (!config.contains(path, true) || config.contains(newPath, true)) {
                return false;
            }
            copy(config, path, config, newPath);
            config.set(path, null);
            return true;
        }, text -> YamlTextPatcher.renameKey(text, parentPath, oldKey, newKey));
    }

    /**
     * Sets plain values in a section of a YAML file the server owner edits (e.g. a camera point in intro.yml), creating
     * the section if needed. Only the affected lines change; comments and formatting stay as they are.
     */
    public static boolean setValues(JavaPlugin plugin, File file, String sectionPath, Map<String, Object> values) {
        return edit(plugin, file, "update " + sectionPath, config -> {
            values.forEach((key, value) -> config.set(sectionPath + "." + key, value));
            return true;
        }, text -> YamlTextPatcher.setValues(text, sectionPath, values));
    }

    /** Removes an option or section from a YAML file the server owner edits; the rest stays as written. */
    public static boolean removeKey(JavaPlugin plugin, File file, String path) {
        return edit(plugin, file, "remove " + path, config -> {
            if (!config.contains(path, true)) {
                return false;
            }
            config.set(path, null);
            return true;
        }, text -> YamlTextPatcher.removeKey(text, path));
    }

    /**
     * Applies a change to a file: {@code change} does it through Bukkit (returning false if there is nothing to do),
     * {@code textChange} does the same on the text. The text version is written if it reads exactly like the Bukkit
     * version, so the file keeps its layout; otherwise the Bukkit version is written.
     */
    private static boolean edit(JavaPlugin plugin, File file, String what, Predicate<YamlConfiguration> change,
                                UnaryOperator<String> textChange) {
        try {
            String text = Files.readString(file.toPath());
            YamlConfiguration expected = parse(text);
            if (!change.test(expected)) {
                return false;
            }
            String edited = textChange.apply(text);
            if (edited == null || readsAs(edited, expected) == null) {
                edited = expected.saveToString();
            }
            AtomicFiles.writeString(file.toPath(), edited);
            return true;
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().warning("Could not " + what + " in " + file.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    static int mergeSection(ConfigurationSection defaults, ConfigurationSection current, Set<String> userSections) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);

            if (!current.contains(key, true)) {
                copy(defaults, key, current, key);
                added++;
                continue;
            }

            if (defaultValue instanceof ConfigurationSection defaultSection
                    && current.isConfigurationSection(key)
                    && !userSections.contains(defaultSection.getCurrentPath())) {
                added += mergeSection(defaultSection, current.getConfigurationSection(key), userSections);
            }
        }
        return added;
    }

    /**
     * Copies an option or a whole section with its comments. Sections are rebuilt in the target instead of
     * being shared, so their paths stay correct and later changes to one side never show up in the other.
     */
    static void copy(ConfigurationSection source, String sourcePath, ConfigurationSection target, String targetPath) {
        if (source.get(sourcePath) instanceof ConfigurationSection section) {
            target.createSection(targetPath);
            for (String key : section.getKeys(false)) {
                copy(source, sourcePath + "." + key, target, targetPath + "." + key);
            }
        } else {
            target.set(targetPath, source.get(sourcePath));
        }
        target.setComments(targetPath, source.getComments(sourcePath));
        target.setInlineComments(targetPath, source.getInlineComments(sourcePath));
    }

    /** Parses a text edited line by line; null unless it holds exactly the options and values of {@code expected}. */
    private static YamlConfiguration readsAs(String text, ConfigurationSection expected) {
        try {
            YamlConfiguration parsed = parse(text);
            return sameContent(parsed, expected) ? parsed : null;
        } catch (InvalidConfigurationException ex) {
            return null;
        }
    }

    /** True if both hold the same options with equal values; comments and formatting don't count. */
    private static boolean sameContent(ConfigurationSection a, ConfigurationSection b) {
        Map<String, Object> left = a.getValues(true);
        Map<String, Object> right = b.getValues(true);
        if (!left.keySet().equals(right.keySet())) {
            return false;
        }
        for (Map.Entry<String, Object> entry : left.entrySet()) {
            Object other = right.get(entry.getKey());
            boolean equal = entry.getValue() instanceof ConfigurationSection
                    ? other instanceof ConfigurationSection
                    : Objects.equals(entry.getValue(), other);
            if (!equal) {
                return false;
            }
        }
        return true;
    }

    private static String readResource(JavaPlugin plugin, String resourcePath) throws IOException {
        try (InputStream in = plugin.getResource(resourcePath)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlConfiguration parse(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().width(LINE_WIDTH);
        yaml.loadFromString(text);
        return yaml;
    }

    private static YamlConfiguration bundled(String text) {
        try {
            return parse(text);
        } catch (InvalidConfigurationException ex) {
            throw new IllegalStateException("A config file bundled with the plugin is not valid YAML", ex);
        }
    }

    /**
     * Outcome of {@link #update}: the content to use, the new file text ({@code null} = leave the file alone),
     * how many options were added, the file's version before and after, and why it couldn't be read.
     */
    record Result(YamlConfiguration config, String text, int addedKeys, int fromVersion, int toVersion, String error) {

        boolean migrated() {
            return fromVersion < toVersion;
        }
    }
}
