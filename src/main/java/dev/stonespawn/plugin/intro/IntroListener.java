package dev.stonespawn.plugin.intro;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class IntroListener implements Listener {

    private final IntroManager intro;

    public IntroListener(IntroManager intro) {
        this.intro = intro;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        intro.recover(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        intro.finish(event.getPlayer(), IntroManager.End.QUIT);
    }

    // Shift is also what makes a spectator let go of the camera, so without skipping it's simply ignored.
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        IntroSession session = intro.session(event.getPlayer());
        if (session == null || !event.isSneaking()) {
            return;
        }
        if (session.skippable()) {
            intro.finish(event.getPlayer(), IntroManager.End.SKIPPED);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        IntroSession session = intro.session(event.getPlayer());
        if (session != null && session.isCamera(event.getSpectatorTarget()) && event.getSpectatorTarget().isValid()) {
            event.setCancelled(true);
        }
    }

    // Following the camera teleports the player with cause SPECTATE; those belong to the intro.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        IntroSession session = intro.session(event.getPlayer());
        if (session == null || event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                || session.isOwnTeleport(event.getTo())) {
            return;
        }
        intro.finish(event.getPlayer(), IntroManager.End.INTERRUPTED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.SPECTATOR && intro.session(event.getPlayer()) != null) {
            intro.finish(event.getPlayer(), IntroManager.End.GAME_MODE_CHANGED);
        }
    }
}
