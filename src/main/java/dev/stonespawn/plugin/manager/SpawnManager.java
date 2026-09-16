package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpawnManager {

    private final StoneSpawn plugin;
    private File dataFile;
    private YamlConfiguration data;

    private final Set<String> joinedPlayers = new HashSet<>();
    private Location cachedSpawn;

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
        cachedSpawn = null;
    }

    public boolean hasSpawn() {
        return data.contains("spawn.world");
    }

    public Location getSpawn() {
        if (cachedSpawn != null) {
            return cachedSpawn.clone();
        }
        if (!hasSpawn()) {
            return null;
        }
        String worldName = data.getString("spawn.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = data.getDouble("spawn.x");
        double y = data.getDouble("spawn.y");
        double z = data.getDouble("spawn.z");
        float yaw = (float) data.getDouble("spawn.yaw");
        float pitch = (float) data.getDouble("spawn.pitch");
        cachedSpawn = new Location(world, x, y, z, yaw, pitch);
        return cachedSpawn.clone();
    }

    public void setSpawn(Location location) {
        data.set("spawn.world", location.getWorld().getName());
        data.set("spawn.x", location.getX());
        data.set("spawn.y", location.getY());
        data.set("spawn.z", location.getZ());
        data.set("spawn.yaw", (double) location.getYaw());
        data.set("spawn.pitch", (double) location.getPitch());
        cachedSpawn = location.clone();
        save();
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
