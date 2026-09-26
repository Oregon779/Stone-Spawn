package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.config.ConfigUpdater;
import dev.stonespawn.plugin.model.BoostTrigger;
import dev.stonespawn.plugin.model.JoinMode;
import dev.stonespawn.plugin.model.MessageDisplayType;
import dev.stonespawn.plugin.model.WorldMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigManager {

    private static final String RESOURCE_PATH = "config.yml";

    private final StoneSpawn plugin;
    private File configFile;
    private YamlConfiguration config;

    private volatile boolean voidTeleportEnabled;
    private volatile double voidHeight;
    private volatile boolean cancelOnMove;
    private volatile boolean elytraEnabled;
    private volatile double elytraRadius;
    private volatile double elytraMinFallDistance;
    private volatile BoostTrigger boostTrigger = BoostTrigger.SWAP_HAND;

    public ConfigManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource(RESOURCE_PATH, false);
        }

        try {
            ConfigUpdater.UpdateResult result = ConfigUpdater.update(plugin, RESOURCE_PATH, configFile);
            if (result.addedKeys() > 0) {
                plugin.getLogger().info("Added " + result.addedKeys() + " new option(s) to config.yml");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to update config.yml: " + ex.getMessage());
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        voidTeleportEnabled = config.getBoolean("spawn.void.enabled", false);
        voidHeight = config.getDouble("spawn.void.height", -64);
        cancelOnMove = config.getBoolean("teleport.cancel-on-move", true);
        elytraEnabled = config.getBoolean("elytra.enabled", false);
        elytraRadius = Math.max(1.0, config.getDouble("elytra.radius", 50));
        elytraMinFallDistance = Math.max(1.5, config.getDouble("elytra.min-fall-distance", 4.0));
        boostTrigger = BoostTrigger.fromConfig(config.getString("elytra.boost.trigger", "SWAP_HAND"), BoostTrigger.SWAP_HAND);
    }

    public void reload() {
        load();
    }

    public boolean isVoidTeleportEnabled() {
        return voidTeleportEnabled;
    }

    public double getVoidHeight() {
        return voidHeight;
    }

    public boolean isCancelOnMove() {
        return cancelOnMove;
    }

    public boolean isElytraEnabled() {
        return elytraEnabled;
    }

    public double getElytraRadius() {
        return elytraRadius;
    }

    public double getElytraMinFallDistance() {
        return elytraMinFallDistance;
    }

    public BoostTrigger getBoostTrigger() {
        return boostTrigger;
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public String getLanguage() {
        return config.getString("language", "en");
    }

    public JoinMode getJoinMode() {
        return JoinMode.fromConfig(getString("spawn.join.mode", "FIRST_JOIN"), JoinMode.FIRST_JOIN);
    }

    public WorldMode getWorldMode() {
        return WorldMode.fromConfig(getString("worlds.mode", "NONE"), WorldMode.NONE);
    }

    public MessageDisplayType getCountdownNotificationType() {
        return MessageDisplayType.fromConfig(getString("teleport.countdown.notification", "ACTIONBAR"),
                MessageDisplayType.ACTIONBAR);
    }

    public MessageDisplayType getArrivalNotificationType() {
        return MessageDisplayType.fromConfig(getString("teleport.arrival.notification", "TITLE"),
                MessageDisplayType.TITLE);
    }
}
