package dev.stonespawn.plugin;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.CompletableFuture;

/** PlayerMock with the few Paper methods MockBukkit doesn't implement, plus a controllable on-ground flag. */
public class TestPlayer extends PlayerMock {

    private boolean grounded = true;
    private final java.util.List<Particle> receivedParticles = new java.util.ArrayList<>();

    public TestPlayer(ServerMock server, String name) {
        super(server, name);
    }

    public TestPlayer(ServerMock server, String name, java.util.UUID uuid) {
        super(server, name, uuid);
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

    @Override
    public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY,
                                  double offsetZ, double extra, T data) {
        receivedParticles.add(particle);
        super.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }

    /** Particles sent to this player only (Player#spawnParticle), like a real client would receive them. */
    public java.util.List<Particle> receivedParticles() {
        return receivedParticles;
    }
}
