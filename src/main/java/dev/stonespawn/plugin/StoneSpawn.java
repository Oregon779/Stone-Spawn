package dev.stonespawn.plugin;

import dev.stonespawn.plugin.command.SetSpawnCommand;
import dev.stonespawn.plugin.command.SpawnCommand;
import dev.stonespawn.plugin.command.StoneSpawnCommand;
import dev.stonespawn.plugin.listener.CommandVisibilityListener;
import dev.stonespawn.plugin.listener.FallDamageListener;
import dev.stonespawn.plugin.listener.PlayerDeathRespawnListener;
import dev.stonespawn.plugin.listener.PlayerJoinListener;
import dev.stonespawn.plugin.listener.PlayerQuitCleanupListener;
import dev.stonespawn.plugin.listener.TeleportMoveListener;
import dev.stonespawn.plugin.listener.VoidTeleportListener;
import dev.stonespawn.plugin.listener.WorldChangeListener;
import dev.stonespawn.plugin.manager.ConfigManager;
import dev.stonespawn.plugin.manager.CooldownManager;
import dev.stonespawn.plugin.manager.EffectManager;
import dev.stonespawn.plugin.manager.MessageManager;
import dev.stonespawn.plugin.manager.NotificationManager;
import dev.stonespawn.plugin.manager.SpawnManager;
import dev.stonespawn.plugin.manager.TeleportManager;
import dev.stonespawn.plugin.manager.UpdateChecker;
import dev.stonespawn.plugin.manager.WorldRestrictionManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoneSpawn extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private SpawnManager spawnManager;
    private CooldownManager cooldownManager;
    private WorldRestrictionManager worldRestrictionManager;
    private TeleportManager teleportManager;
    private NotificationManager notificationManager;
    private EffectManager effectManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this);
        messageManager.load();

        spawnManager = new SpawnManager(this);
        spawnManager.load();

        cooldownManager = new CooldownManager();
        worldRestrictionManager = new WorldRestrictionManager(this);
        teleportManager = new TeleportManager(this);
        notificationManager = new NotificationManager(this);
        effectManager = new EffectManager(this);
        updateChecker = new UpdateChecker(this);

        registerCommands();
        registerListeners();
        updateChecker.start();
        effectManager.startSpawnMarker();

        getLogger().info("Config loaded (" + configManager.getLanguage() + "), spawn point "
                + (spawnManager.hasSpawn() ? "set" : "not set - use /setspawn")
                + " - commands, listeners and update checker ready.");
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (effectManager != null) {
            effectManager.stopSpawnMarker();
        }
        getLogger().info("StoneSpawn has been disabled.");
    }

    public void reload() {
        configManager.reload();
        messageManager.load();
        worldRestrictionManager.refresh();
        effectManager.startSpawnMarker();
    }

    private void registerCommands() {
        getCommand("setspawn").setExecutor(new SetSpawnCommand(this));

        SpawnCommand spawnCommand = new SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCommand);
        getCommand("spawn").setTabCompleter(spawnCommand);

        StoneSpawnCommand stoneSpawnCommand = new StoneSpawnCommand(this);
        getCommand("stonespawn").setExecutor(stoneSpawnCommand);
        getCommand("stonespawn").setTabCompleter(stoneSpawnCommand);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerDeathRespawnListener(this), this);
        pm.registerEvents(new VoidTeleportListener(this), this);
        pm.registerEvents(new WorldChangeListener(this), this);
        pm.registerEvents(new TeleportMoveListener(this), this);
        pm.registerEvents(new FallDamageListener(this), this);
        pm.registerEvents(new CommandVisibilityListener(this), this);
        pm.registerEvents(new PlayerQuitCleanupListener(this), this);
        pm.registerEvents(updateChecker, this);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public WorldRestrictionManager getWorldRestrictionManager() {
        return worldRestrictionManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
