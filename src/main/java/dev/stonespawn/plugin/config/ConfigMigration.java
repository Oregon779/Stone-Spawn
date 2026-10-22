package dev.stonespawn.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One step of a versioned file migration: brings a file from {@code version - 1} to {@code version} by
 * moving, renaming or removing options. Steps only touch what they name, so running one again on an
 * already migrated file changes nothing.
 */
public final class ConfigMigration {

    private final int version;
    private final List<Consumer<ConfigurationSection>> actions = new ArrayList<>();

    private ConfigMigration(int version) {
        this.version = version;
    }

    /** Starts the step that updates a file to {@code version} (the new "config-version" of the bundled file). */
    public static ConfigMigration to(int version) {
        return new ConfigMigration(version);
    }

    /**
     * Moves an option or a whole section to a new path, together with its comments. Nothing happens if the
     * old path does not exist or the new one is already taken, so no value is ever overwritten.
     */
    public ConfigMigration move(String from, String to) {
        actions.add(config -> {
            if (!config.contains(from, true) || config.contains(to, true)) {
                return;
            }
            ConfigUpdater.copy(config, from, config, to);
            config.set(from, null);
        });
        return this;
    }

    /** Deletes an option or section that no longer exists. */
    public ConfigMigration remove(String path) {
        actions.add(config -> config.set(path, null));
        return this;
    }

    /** Anything that is not a plain move or removal, e.g. converting a value to a new format. */
    public ConfigMigration custom(Consumer<ConfigurationSection> action) {
        actions.add(action);
        return this;
    }

    public int version() {
        return version;
    }

    void apply(ConfigurationSection config) {
        for (Consumer<ConfigurationSection> action : actions) {
            action.accept(config);
        }
    }
}
