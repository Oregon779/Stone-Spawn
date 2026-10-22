package dev.stonespawn.plugin.config;

import java.util.List;
import java.util.Set;

/**
 * How one bundled YAML file is kept up to date on servers that already have an older copy of it.
 *
 * <p>New options need nothing here: every option of the bundled file that is missing on the server is
 * added on the next start, with its comments and default value. Only when an existing option is renamed,
 * moved, removed or changes its meaning:
 * <ol>
 *   <li>raise {@value #VERSION_KEY} in the bundled file by one (e.g. 1 to 2),</li>
 *   <li>add the matching step to the file's list below, e.g.
 *       {@code ConfigMigration.to(2).move("teleport.delay", "teleport.delay-seconds")}.</li>
 * </ol>
 * A file without {@value #VERSION_KEY} counts as version 1, so a file can start using versions at any time.
 *
 * @param migrations   steps in ascending version order, starting at 2
 * @param userSections sections whose entries belong to the server owner, e.g. the GUI's own items: they are
 *                     created when missing, but bundled example entries are never added to an existing one
 */
public record ConfigSchema(List<ConfigMigration> migrations, Set<String> userSections) {

    public static final String VERSION_KEY = "config-version";

    public static final ConfigSchema CONFIG = new ConfigSchema(List.of(), Set.of());

    public static final ConfigSchema GUI = new ConfigSchema(List.of(), Set.of("spawns", "items"));

    public static final ConfigSchema MESSAGES = new ConfigSchema(List.of(), Set.of());

    public ConfigSchema {
        migrations = List.copyOf(migrations);
        userSections = Set.copyOf(userSections);
        int previous = 1;
        for (ConfigMigration migration : migrations) {
            if (migration.version() <= previous) {
                throw new IllegalArgumentException("Config migrations must have ascending versions starting at 2, got "
                        + migration.version() + " after " + previous);
            }
            previous = migration.version();
        }
    }

    /** The version the bundled file must declare: the last migration's, or 1 without migrations. */
    public int latestVersion() {
        return migrations.isEmpty() ? 1 : migrations.getLast().version();
    }
}
