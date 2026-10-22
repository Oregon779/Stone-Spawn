package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.config.ConfigSchema;
import dev.stonespawn.plugin.config.ConfigUpdater;
import dev.stonespawn.plugin.model.BoostTrigger;
import dev.stonespawn.plugin.model.JoinMode;
import dev.stonespawn.plugin.model.MessageDisplayType;
import dev.stonespawn.plugin.model.WorldMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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
    private volatile BoostTrigger bedrockBoostTrigger = BoostTrigger.SNEAK;

    public ConfigManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), RESOURCE_PATH);
        // Migrates and completes the file first, so every value below is read from the up-to-date layout.
        config = ConfigUpdater.load(plugin, RESOURCE_PATH, configFile, ConfigSchema.CONFIG);

        voidTeleportEnabled = config.getBoolean("spawn.void.enabled", false);
        voidHeight = config.getDouble("spawn.void.height", -64);
        cancelOnMove = config.getBoolean("teleport.cancel-on-move", true);
        elytraEnabled = config.getBoolean("elytra.enabled", false);
        elytraRadius = Math.max(1.0, config.getDouble("elytra.radius", 50));
        elytraMinFallDistance = Math.max(1.5, config.getDouble("elytra.min-fall-distance", 4.0));
        boostTrigger = BoostTrigger.fromConfig(config.getString("elytra.boost.trigger", "SWAP_HAND"), BoostTrigger.SWAP_HAND);
        bedrockBoostTrigger = BoostTrigger.fromConfig(config.getString("elytra.boost.bedrock-trigger", "SNEAK"), BoostTrigger.SNEAK);
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

    public BoostTrigger getBedrockBoostTrigger() {
        return bedrockBoostTrigger;
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
