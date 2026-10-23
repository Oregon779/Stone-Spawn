package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.util.AtomicFiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class SpawnManager implements Listener {

    /** Name the main spawn gets when it is created with /setspawn without a name. It can be renamed later. */
    public static final String MAIN_SPAWN = "spawn";

    private static final String MAIN_PATH = "main-spawn";
    private static final String SPAWNS_PATH = "spawns";
    private static final String LEGACY_SPAWN_PATH = "spawn";
    private static final String JOINED_PATH = "joined-players";
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final long JOIN_SAVE_DELAY_TICKS = 100L;

    private final StoneSpawn plugin;
    private File dataFile;

    private final Map<String, StoredSpawn> spawns = new HashMap<>();
    private final Set<String> joinedPlayers = new HashSet<>();
    // Name of the spawn that join, death, void and world-change teleports use; null if there is none.
    private String mainSpawn;

    private List<String> sortedNames = List.of();
    private Map<String, Location> resolvedSpawns = Map.of();
    private List<Location> resolvedLocations = List.of();

    private final AtomicLong changeCounter = new AtomicLong();
    private final Object writeLock = new Object();
    private long lastWrittenVersion;
    private BukkitTask scheduledSave;

    public SpawnManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration data = readDataFile();

        spawns.clear();
        joinedPlayers.clear();
        joinedPlayers.addAll(data.getStringList(JOINED_PATH));

        ConfigurationSection section = data.getConfigurationSection(SPAWNS_PATH);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                StoredSpawn spawn = StoredSpawn.read(section.getConfigurationSection(key));
                if (spawn != null) {
                    spawns.put(normalize(key), spawn);
                }
            }
        }

        boolean migrated = false;
        StoredSpawn legacy = StoredSpawn.read(data.getConfigurationSection(LEGACY_SPAWN_PATH));
        if (legacy != null) {
            spawns.putIfAbsent(MAIN_SPAWN, legacy);
            migrated = true;
            plugin.getLogger().info("Moved the existing spawn point to the new multi-spawn format as '" + MAIN_SPAWN + "'.");
        }

        mainSpawn = null;
        if (data.isString(MAIN_PATH)) {
            String main = normalize(data.getString(MAIN_PATH));
            mainSpawn = spawns.containsKey(main) ? main : null;
        } else if (spawns.containsKey(MAIN_SPAWN)) {
            // Saved before spawns could be renamed: the main spawn was the one called "spawn".
            mainSpawn = MAIN_SPAWN;
        }

        rebuildCache();
        if (migrated) {
            markDirty(true);
        }
    }

    private YamlConfiguration readDataFile() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        if (!dataFile.exists()) {
            return yaml;
        }
        try {
            yaml.load(dataFile);
            return yaml;
        } catch (IOException | InvalidConfigurationException ex) {
            Path backup = dataFile.toPath().resolveSibling("data.yml.broken-" + System.currentTimeMillis());
            try {
                Files.copy(dataFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyEx) {
                plugin.getLogger().severe("Could not back up the unreadable data.yml: " + copyEx.getMessage());
            }
            plugin.getLogger().severe("data.yml could not be read (" + ex.getMessage() + "). A copy was kept as "
                    + backup.getFileName() + " - starting without spawn points.");
            return new YamlConfiguration();
        }
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static boolean isValidName(String name) {
        return NAME_PATTERN.matcher(normalize(name)).matches();
    }

    public boolean hasSpawn() {
        return mainSpawn != null;
    }

    public boolean hasSpawn(String name) {
        return spawns.containsKey(normalize(name));
    }

    public Location getSpawn() {
        return mainSpawn == null ? null : getSpawn(mainSpawn);
    }

    /** @return the name of the main spawn, or null if there is none. */
    public String getMainSpawnName() {
        return mainSpawn;
    }

    /** @return a copy of the spawn location, or null if it doesn't exist or its world isn't loaded. */
    public Location getSpawn(String name) {
        Location location = resolvedSpawns.get(normalize(name));
        return location != null && location.isWorldLoaded() ? location.clone() : null;
    }

    /** Main spawn first, then all other spawns alphabetically. */
    public List<String> getSpawnNames() {
        return sortedNames;
    }

    public int getSpawnCount() {
        return spawns.size();
    }

    /** Spawns in loaded worlds. Shared, cached instances - callers must not modify them. */
    public List<Location> getAllSpawnLocations() {
        return resolvedLocations;
    }

    /** Moves the main spawn, whatever it is called, or creates it as "spawn" if there is none. */
    public void setSpawn(Location location) {
        setSpawn(mainSpawn != null ? mainSpawn : MAIN_SPAWN, location);
    }

    public void setSpawn(String name, Location location) {
        String key = normalize(name);
        spawns.put(key, StoredSpawn.of(location));
        if (mainSpawn == null && key.equals(MAIN_SPAWN)) {
            // Keeps "/setspawn spawn" meaning the same as "/setspawn" while there is no main spawn.
            mainSpawn = key;
        }
        rebuildCache();
        markDirty(true);
    }

    public boolean deleteSpawn(String name) {
        String key = normalize(name);
        if (spawns.remove(key) == null) {
            return false;
        }
        if (key.equals(mainSpawn)) {
            mainSpawn = null;
        }
        rebuildCache();
        markDirty(true);
        return true;
    }

    /** Renames a spawn; the main spawn stays the main spawn. The new name must already be validated. */
    public RenameResult renameSpawn(String oldName, String newName) {
        String from = normalize(oldName);
        String to = normalize(newName);
        if (!spawns.containsKey(from)) {
            return RenameResult.NOT_FOUND;
        }
        if (spawns.containsKey(to)) {
            return RenameResult.NAME_TAKEN;
        }
        spawns.put(to, spawns.remove(from));
        if (from.equals(mainSpawn)) {
            mainSpawn = to;
        }
        rebuildCache();
        markDirty(true);
        return RenameResult.RENAMED;
    }

    public enum RenameResult {
        RENAMED, NOT_FOUND, NAME_TAKEN
    }

    public boolean hasJoinedBefore(UUID uuid) {
        return joinedPlayers.contains(uuid.toString());
    }

    public void markJoined(UUID uuid) {
        if (joinedPlayers.add(uuid.toString())) {
            markDirty(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        rebuildCache();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::rebuildCache);
    }

    private void rebuildCache() {
        List<String> names = new ArrayList<>(spawns.keySet());
        names.sort((a, b) -> {
            if (a.equals(mainSpawn) || b.equals(mainSpawn)) {
                return a.equals(b) ? 0 : (a.equals(mainSpawn) ? -1 : 1);
            }
            return a.compareTo(b);
        });

        Map<String, Location> resolved = new HashMap<>();
        List<Location> locations = new ArrayList<>();
        for (String name : names) {
            Location location = spawns.get(name).toLocation();
            if (location != null) {
                resolved.put(name, location);
                locations.add(location);
            }
        }
        sortedNames = Collections.unmodifiableList(names);
        resolvedSpawns = resolved;
        resolvedLocations = Collections.unmodifiableList(locations);
    }

    /** Writes pending changes right away on the calling thread. Used on shutdown. */
    public void flush() {
        if (scheduledSave != null) {
            scheduledSave.cancel();
            scheduledSave = null;
        }
        write(snapshot());
    }

    private void markDirty(boolean urgent) {
        changeCounter.incrementAndGet();
        if (urgent) {
            if (scheduledSave != null) {
                scheduledSave.cancel();
                scheduledSave = null;
            }
            saveAsync();
        } else if (scheduledSave == null) {
            // Batches bursts of first joins into a single write.
            scheduledSave = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                scheduledSave = null;
                saveAsync();
            }, JOIN_SAVE_DELAY_TICKS);
        }
    }

    private void saveAsync() {
        Snapshot snapshot = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(snapshot));
    }

    private Snapshot snapshot() {
        return new Snapshot(changeCounter.get(), mainSpawn, Map.copyOf(spawns), new ArrayList<>(joinedPlayers));
    }

    // Async saves may run concurrently and out of order; the lock plus version check keeps only the newest state.
    private void write(Snapshot snapshot) {
        synchronized (writeLock) {
            if (snapshot.version() <= lastWrittenVersion) {
                return;
            }
            try {
                AtomicFiles.writeString(dataFile.toPath(), snapshot.toYaml());
                lastWrittenVersion = snapshot.version();
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
            }
        }
    }

    private record Snapshot(long version, String mainSpawn, Map<String, StoredSpawn> spawns, List<String> joinedPlayers) {

        String toYaml() {
            YamlConfiguration yaml = new YamlConfiguration();
            // Written even when empty, so a deleted main spawn isn't brought back by a spawn called "spawn".
            yaml.set(MAIN_PATH, mainSpawn == null ? "" : mainSpawn);
            new ArrayList<>(spawns.keySet()).stream().sorted()
                    .forEach(name -> spawns.get(name).write(yaml, SPAWNS_PATH + "." + name));
            List<String> joined = new ArrayList<>(joinedPlayers);
            Collections.sort(joined);
            yaml.set(JOINED_PATH, joined);
            return yaml.saveToString();
        }
    }

    private record StoredSpawn(String world, double x, double y, double z, float yaw, float pitch) {

        static StoredSpawn of(Location location) {
            return new StoredSpawn(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }

        static StoredSpawn read(ConfigurationSection section) {
            if (section == null || !section.isString("world")) {
                return null;
            }
            return new StoredSpawn(section.getString("world"), section.getDouble("x"), section.getDouble("y"),
                    section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        }

        Location toLocation() {
            World loaded = Bukkit.getWorld(world);
            return loaded == null ? null : new Location(loaded, x, y, z, yaw, pitch);
        }

        void write(YamlConfiguration yaml, String path) {
            yaml.set(path + ".world", world);
            yaml.set(path + ".x", x);
            yaml.set(path + ".y", y);
            yaml.set(path + ".z", z);
            yaml.set(path + ".yaw", (double) yaw);
            yaml.set(path + ".pitch", (double) pitch);
        }
    }
}
