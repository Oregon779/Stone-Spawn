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
    private final java.util.List<net.kyori.adventure.title.Title> shownTitles = new java.util.ArrayList<>();
    private final java.util.List<net.kyori.adventure.inventory.Book> openedBooks = new java.util.ArrayList<>();
    private final java.util.List<net.kyori.adventure.text.Component> subtitleUpdates = new java.util.ArrayList<>();
    private final java.util.List<String> stoppedSounds = new java.util.ArrayList<>();
    private boolean playedBefore;
    private org.bukkit.block.Block targetBlock;
    private org.bukkit.event.player.PlayerResourcePackStatusEvent.Status resourcePackStatus;

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

    @Override
    public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY,
                                  double offsetZ, double extra, T data, boolean force) {
        receivedParticles.add(particle);
    }

    @Override
    public <T> void sendTitlePart(net.kyori.adventure.title.TitlePart<T> part, T value) {
        if (part == net.kyori.adventure.title.TitlePart.SUBTITLE) {
            subtitleUpdates.add((net.kyori.adventure.text.Component) value);
        }
    }

    public java.util.List<net.kyori.adventure.text.Component> subtitleUpdates() {
        return subtitleUpdates;
    }

    @Override
    public void stopSound(org.bukkit.Sound sound, org.bukkit.SoundCategory category) {
        stoppedSounds.add(sound.key().asString());
    }

    @Override
    public void stopSound(org.bukkit.SoundCategory category) {
        stoppedSounds.add(category.name());
    }

    public java.util.List<String> stoppedSounds() {
        return stoppedSounds;
    }

    @Override
    public void showTitle(net.kyori.adventure.title.Title title) {
        shownTitles.add(title);
    }

    public java.util.List<net.kyori.adventure.title.Title> shownTitles() {
        return shownTitles;
    }

    @Override
    public void openBook(net.kyori.adventure.inventory.Book book) {
        openedBooks.add(book);
    }

    public java.util.List<net.kyori.adventure.inventory.Book> openedBooks() {
        return openedBooks;
    }

    @Override
    public org.bukkit.event.player.PlayerResourcePackStatusEvent.Status getResourcePackStatus() {
        return resourcePackStatus;
    }

    /** The status of a resource pack loaded before joining, like one from server.properties. */
    public void setResourcePackStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent.Status status) {
        this.resourcePackStatus = status;
    }

    /** MockBukkit can't trace what a player looks at; tests say it here. */
    public void setTargetBlock(org.bukkit.block.Block block) {
        this.targetBlock = block;
    }

    @Override
    public org.bukkit.block.Block getTargetBlockExact(int maxDistance) {
        return targetBlock;
    }

    @Override
    public boolean hasPlayedBefore() {
        return playedBefore;
    }

    public void setPlayedBefore(boolean playedBefore) {
        this.playedBefore = playedBefore;
    }
}
