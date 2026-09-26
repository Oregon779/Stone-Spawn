package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.model.MessageDisplayType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationManager {

    private final StoneSpawn plugin;
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> bossBarHideTasks = new ConcurrentHashMap<>();

    private static final Map<String, String> BOSSBAR_COLOR_ALIASES = Map.of(
            "GOLD", "YELLOW",
            "ORANGE", "YELLOW",
            "MAGENTA", "PURPLE",
            "CYAN", "BLUE",
            "GRAY", "WHITE",
            "GREY", "WHITE"
    );

    private static final Map<String, String> BOSSBAR_STYLE_ALIASES = Map.of(
            "SOLID", "PROGRESS",
            "BAR", "PROGRESS",
            "FULL", "PROGRESS",
            "NONE", "PROGRESS",
            "SEGMENTED", "NOTCHED_10",
            "SEGMENTS", "NOTCHED_10"
    );

    public NotificationManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public void hideAll() {
        bossBarHideTasks.values().forEach(BukkitTask::cancel);
        bossBarHideTasks.clear();
        activeBossBars.forEach((uuid, bar) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.hideBossBar(bar);
            }
        });
        activeBossBars.clear();
    }

    public void clear(Player player) {
        BukkitTask hideTask = bossBarHideTasks.remove(player.getUniqueId());
        if (hideTask != null) {
            hideTask.cancel();
        }
        BossBar bar = activeBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void sendCountdown(Player player, int secondsRemaining) {
        Map<String, String> placeholders = Map.of("seconds", String.valueOf(secondsRemaining));
        MessageDisplayType type = plugin.getConfigManager().getCountdownNotificationType();
        dispatch(player, "teleport.countdown", type, placeholders);
    }

    public void sendArrival(Player player) {
        MessageDisplayType type = plugin.getConfigManager().getArrivalNotificationType();
        dispatch(player, "teleport.arrival", type, Map.of());
    }

    private void dispatch(Player player, String section, MessageDisplayType type, Map<String, String> placeholders) {
        ConfigManager cfg = plugin.getConfigManager();
        MessageManager mm = plugin.getMessageManager();

        switch (type) {
            case CHAT -> player.sendMessage(mm.format(cfg.getString(section + ".messages.chat", ""), placeholders));
            case ACTIONBAR -> player.sendActionBar(mm.format(cfg.getString(section + ".messages.actionbar", ""), placeholders));
            case BOSSBAR -> showBossBar(player, mm.format(cfg.getString(section + ".messages.bossbar", ""), placeholders));
            case TITLE -> showTitle(player,
                    mm.format(cfg.getString(section + ".messages.title", ""), placeholders),
                    mm.format(cfg.getString(section + ".messages.subtitle", ""), placeholders));
        }
    }

    private void showTitle(Player player, Component title, Component subtitle) {
        ConfigManager cfg = plugin.getConfigManager();
        int fadeIn = cfg.getInt("teleport.title.fade-in-ticks", 5);
        int stay = cfg.getInt("teleport.title.stay-ticks", 30);
        int fadeOut = cfg.getInt("teleport.title.fade-out-ticks", 5);

        Title.Times times = Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L));
        player.showTitle(Title.title(title, subtitle, times));
    }

    private void showBossBar(Player player, Component component) {
        ConfigManager cfg = plugin.getConfigManager();
        String colorName = cfg.getString("teleport.bossbar.color", "PURPLE");
        String styleName = cfg.getString("teleport.bossbar.style", "PROGRESS");
        int duration = cfg.getInt("teleport.bossbar.duration-seconds", 5);

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            String alias = BOSSBAR_COLOR_ALIASES.get(colorName.toUpperCase());
            color = alias != null ? BossBar.Color.valueOf(alias) : BossBar.Color.PURPLE;
        }
        BossBar.Overlay overlay;
        try {
            overlay = BossBar.Overlay.valueOf(styleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            String alias = BOSSBAR_STYLE_ALIASES.get(styleName.toUpperCase());
            overlay = alias != null ? BossBar.Overlay.valueOf(alias) : BossBar.Overlay.PROGRESS;
        }

        // Updating the shown bar sends one packet; replacing it (hide + show) would send two every countdown second.
        BossBar bar = activeBossBars.get(player.getUniqueId());
        if (bar != null) {
            bar.name(component).color(color).overlay(overlay);
        } else {
            bar = BossBar.bossBar(component, 1.0f, color, overlay);
            player.showBossBar(bar);
            activeBossBars.put(player.getUniqueId(), bar);
        }
        BukkitTask existingHideTask = bossBarHideTasks.remove(player.getUniqueId());
        if (existingHideTask != null) {
            existingHideTask.cancel();
        }

        BukkitTask hideTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BossBar current = activeBossBars.remove(player.getUniqueId());
            if (current != null) {
                player.hideBossBar(current);
            }
            bossBarHideTasks.remove(player.getUniqueId());
        }, Math.max(1, duration) * 20L);
        bossBarHideTasks.put(player.getUniqueId(), hideTask);
    }
}
