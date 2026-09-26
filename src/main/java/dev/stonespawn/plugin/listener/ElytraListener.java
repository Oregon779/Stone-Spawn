package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.ElytraManager;
import dev.stonespawn.plugin.model.BoostTrigger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class ElytraListener implements Listener {

    private final StoneSpawn plugin;
    private final ElytraManager elytra;

    public ElytraListener(StoneSpawn plugin) {
        this.plugin = plugin;
        this.elytra = plugin.getElytraManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        elytra.handleMove(event.getPlayer(), to);
    }

    // Without an elytra item the server switches gliding off every tick; cancelling that keeps the glide alive.
    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() && event.getEntity() instanceof Player player && elytra.shouldKeepGliding(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (isTrigger(BoostTrigger.SWAP_HAND) && elytra.isGliding(player.getUniqueId())) {
            event.setCancelled(true);
            elytra.tryBoost(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && isTrigger(BoostTrigger.SNEAK)) {
            elytra.tryBoost(event.getPlayer());
        }
    }

    // Left clicks into the air arrive as already-cancelled events, so cancelled ones must not be ignored here.
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) && isTrigger(BoostTrigger.LEFT_CLICK)) {
            elytra.tryBoost(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FALL && cause != EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            return;
        }
        if (event.getEntity() instanceof Player player && elytra.isDamageProtected(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        elytra.reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        elytra.reset(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        elytra.reset(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        elytra.clear(event.getPlayer().getUniqueId());
    }

    private boolean isTrigger(BoostTrigger trigger) {
        return plugin.getConfigManager().isElytraEnabled() && plugin.getConfigManager().getBoostTrigger() == trigger;
    }
}
