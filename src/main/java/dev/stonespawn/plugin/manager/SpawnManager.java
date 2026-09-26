package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class SpawnManager {

    public static final String MAIN_SPAWN = "spawn";

    private static final String SPAWNS_PATH = "spawns";
    private static final String LEGACY_SPAWN_PATH = "spawn";
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    private final StoneSpawn plugin;
    private File dataFile;
    private YamlConfiguration data;

    private final Set<String> joinedPlayers = new HashSet<>();
    private final Map<String, Location> resolvedSpawns = new HashMap<>();

    public SpawnManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not create data.yml: " + ex.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        joinedPlayers.clear();
        joinedPlayers.addAll(data.getStringList("joined-players"));
        resolvedSpawns.clear();
        migrateLegacySpawn();
    }

    private void migrateLegacySpawn() {
        ConfigurationSection legacy = data.getConfigurationSection(LEGACY_SPAWN_PATH);
        if (legacy == null || !legacy.contains("world")) {
            return;
        }
        if (!hasSpawn(MAIN_SPAWN)) {
            data.createSection(path(MAIN_SPAWN), legacy.getValues(false));
        }
        data.set(LEGACY_SPAWN_PATH, null);
        save();
        plugin.getLogger().info("Moved the existing spawn point to the new multi-spawn format as '" + MAIN_SPAWN + "'.");
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
        return data.contains(path(normalize(name)) + ".world");
    }

    public Location getSpawn() {
        return getSpawn(MAIN_SPAWN);
    }

    public Location getSpawn(String name) {
        String key = normalize(name);
        Location cached = resolvedSpawns.get(key);
        if (cached != null) {
            if (cached.isWorldLoaded()) {
                return cached.clone();
            }
            resolvedSpawns.remove(key);
        }
        ConfigurationSection section = data.getConfigurationSection(path(key));
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        Location location = new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        resolvedSpawns.put(key, location);
        return location.clone();
    }

    /** Main spawn first, then all other spawns alphabetically. */
    public List<String> getSpawnNames() {
        ConfigurationSection section = data.getConfigurationSection(SPAWNS_PATH);
        if (section == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(section.getKeys(false));
        names.sort((a, b) -> {
            if (a.equals(MAIN_SPAWN)) {
                return b.equals(MAIN_SPAWN) ? 0 : -1;
            }
            if (b.equals(MAIN_SPAWN)) {
                return 1;
            }
            return a.compareTo(b);
        });
        return names;
    }

    public int getSpawnCount() {
        ConfigurationSection section = data.getConfigurationSection(SPAWNS_PATH);
        return section == null ? 0 : section.getKeys(false).size();
    }

    public List<Location> getAllSpawnLocations() {
        List<Location> locations = new ArrayList<>();
        for (String name : getSpawnNames()) {
            Location location = getSpawn(name);
            if (location != null) {
                locations.add(location);
            }
        }
        return locations;
    }

    public void setSpawn(Location location) {
        setSpawn(MAIN_SPAWN, location);
    }

    public void setSpawn(String name, Location location) {
        String key = normalize(name);
        String base = path(key);
        data.set(base + ".world", location.getWorld().getName());
        data.set(base + ".x", location.getX());
        data.set(base + ".y", location.getY());
        data.set(base + ".z", location.getZ());
        data.set(base + ".yaw", (double) location.getYaw());
        data.set(base + ".pitch", (double) location.getPitch());
        resolvedSpawns.put(key, location.clone());
        save();
    }

    public boolean deleteSpawn(String name) {
        String key = normalize(name);
        if (!hasSpawn(key)) {
            return false;
        }
        data.set(path(key), null);
        resolvedSpawns.remove(key);
        save();
        return true;
    }

    public boolean hasJoinedBefore(UUID uuid) {
        return joinedPlayers.contains(uuid.toString());
    }

    public void markJoined(UUID uuid) {
        String id = uuid.toString();
        if (joinedPlayers.add(id)) {
            data.set("joined-players", new ArrayList<>(joinedPlayers));
            save();
        }
    }

    private static String path(String key) {
        return SPAWNS_PATH + "." + key;
    }

    private void save() {
        String snapshot = data.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.writeString(dataFile.toPath(), snapshot, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
            }
        });
    }
}
