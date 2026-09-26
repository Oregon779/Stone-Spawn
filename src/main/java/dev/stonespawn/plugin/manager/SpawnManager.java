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

    public static final String MAIN_SPAWN = "spawn";

    private static final String SPAWNS_PATH = "spawns";
    private static final String LEGACY_SPAWN_PATH = "spawn";
    private static final String JOINED_PATH = "joined-players";
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final long JOIN_SAVE_DELAY_TICKS = 100L;

    private final StoneSpawn plugin;
    private File dataFile;

    private final Map<String, StoredSpawn> spawns = new HashMap<>();
    private final Set<String> joinedPlayers = new HashSet<>();

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
        return hasSpawn(MAIN_SPAWN);
    }

    public boolean hasSpawn(String name) {
        return spawns.containsKey(normalize(name));
    }

    public Location getSpawn() {
        return getSpawn(MAIN_SPAWN);
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

    public void setSpawn(Location location) {
        setSpawn(MAIN_SPAWN, location);
    }

    public void setSpawn(String name, Location location) {
        spawns.put(normalize(name), StoredSpawn.of(location));
        rebuildCache();
        markDirty(true);
    }

    public boolean deleteSpawn(String name) {
        if (spawns.remove(normalize(name)) == null) {
            return false;
        }
        rebuildCache();
        markDirty(true);
        return true;
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
            if (a.equals(MAIN_SPAWN) || b.equals(MAIN_SPAWN)) {
                return a.equals(b) ? 0 : (a.equals(MAIN_SPAWN) ? -1 : 1);
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
        return new Snapshot(changeCounter.get(), Map.copyOf(spawns), new ArrayList<>(joinedPlayers));
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

    private record Snapshot(long version, Map<String, StoredSpawn> spawns, List<String> joinedPlayers) {

        String toYaml() {
            YamlConfiguration yaml = new YamlConfiguration();
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
