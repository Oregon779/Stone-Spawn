package dev.stonespawn.plugin;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.CompletableFuture;

/** PlayerMock with the few Paper methods MockBukkit doesn't implement, plus a controllable on-ground flag. */
public class TestPlayer extends PlayerMock {

    private boolean grounded = true;

    public TestPlayer(ServerMock server, String name) {
        super(server, name);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location, PlayerTeleportEvent.TeleportCause cause,
                                                    TeleportFlag... flags) {
        return CompletableFuture.completedFuture(teleport(location, cause));
    }

    @Override
    public boolean isInLava() {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOnGround() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }
}
