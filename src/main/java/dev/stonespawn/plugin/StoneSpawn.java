package dev.stonespawn.plugin;

import dev.stonespawn.plugin.command.DelSpawnCommand;
import dev.stonespawn.plugin.command.SetSpawnCommand;
import dev.stonespawn.plugin.command.SpawnCommand;
import dev.stonespawn.plugin.command.StoneSpawnCommand;
import dev.stonespawn.plugin.gui.SpawnGui;
import dev.stonespawn.plugin.gui.SpawnGuiListener;
import dev.stonespawn.plugin.listener.CommandVisibilityListener;
import dev.stonespawn.plugin.listener.ElytraListener;
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
import dev.stonespawn.plugin.manager.ElytraManager;
import dev.stonespawn.plugin.manager.MessageManager;
import dev.stonespawn.plugin.manager.NotificationManager;
import dev.stonespawn.plugin.manager.SpawnManager;
import dev.stonespawn.plugin.manager.TeleportManager;
import dev.stonespawn.plugin.manager.UpdateChecker;
import dev.stonespawn.plugin.manager.WorldRestrictionManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

// Not final: MockBukkit subclasses the plugin main class in tests.
public class StoneSpawn extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private SpawnManager spawnManager;
    private CooldownManager cooldownManager;
    private WorldRestrictionManager worldRestrictionManager;
    private TeleportManager teleportManager;
    private NotificationManager notificationManager;
    private EffectManager effectManager;
    private UpdateChecker updateChecker;
    private ElytraManager elytraManager;
    private SpawnGui spawnGui;

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
        effectManager.reload();
        updateChecker = new UpdateChecker(this);
        elytraManager = new ElytraManager(this);
        spawnGui = new SpawnGui(this);
        spawnGui.load();

        registerCommands();
        registerListeners();
        updateChecker.start();
        effectManager.startSpawnMarker();

        getLogger().info("Config loaded (" + configManager.getLanguage() + "), "
                + spawnManager.getSpawnCount() + " spawn point(s)"
                + (spawnManager.hasSpawn() ? "" : ", main spawn not set - use /setspawn")
                + " - commands, listeners and update checker ready.");
    }

    @Override
    public void onDisable() {
        if (spawnGui != null) {
            spawnGui.closeAll();
        }
        if (notificationManager != null) {
            notificationManager.hideAll();
        }
        if (spawnManager != null) {
            spawnManager.flush();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
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
        spawnGui.load();
        effectManager.reload();
        effectManager.startSpawnMarker();
        updateChecker.start();
    }

    private void registerCommands() {
        SetSpawnCommand setSpawnCommand = new SetSpawnCommand(this);
        getCommand("setspawn").setExecutor(setSpawnCommand);
        getCommand("setspawn").setTabCompleter(setSpawnCommand);

        DelSpawnCommand delSpawnCommand = new DelSpawnCommand(this);
        getCommand("delspawn").setExecutor(delSpawnCommand);
        getCommand("delspawn").setTabCompleter(delSpawnCommand);

        SpawnCommand spawnCommand = new SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCommand);
        getCommand("spawn").setTabCompleter(spawnCommand);

        StoneSpawnCommand stoneSpawnCommand = new StoneSpawnCommand(this);
        getCommand("stonespawn").setExecutor(stoneSpawnCommand);
        getCommand("stonespawn").setTabCompleter(stoneSpawnCommand);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(spawnManager, this);
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerDeathRespawnListener(this), this);
        pm.registerEvents(new VoidTeleportListener(this), this);
        pm.registerEvents(new WorldChangeListener(this), this);
        pm.registerEvents(new TeleportMoveListener(this), this);
        pm.registerEvents(new FallDamageListener(this), this);
        pm.registerEvents(new CommandVisibilityListener(this), this);
        pm.registerEvents(new PlayerQuitCleanupListener(this), this);
        pm.registerEvents(updateChecker, this);
        pm.registerEvents(new ElytraListener(this), this);
        pm.registerEvents(new SpawnGuiListener(spawnGui), this);
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

    public ElytraManager getElytraManager() {
        return elytraManager;
    }

    public SpawnGui getSpawnGui() {
        return spawnGui;
    }
}
