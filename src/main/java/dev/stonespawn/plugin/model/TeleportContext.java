package dev.stonespawn.plugin.model;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

public record TeleportContext(boolean instant, boolean silent, Consumer<Player> onCompleteCallback) {

    public static TeleportContext instantContext() {
        return new TeleportContext(true, false, null);
    }

    public static TeleportContext instantSilentContext() {
        return new TeleportContext(true, true, null);
    }

    public static TeleportContext commandContext() {
        return new TeleportContext(false, false, null);
    }

    public void onComplete(Player player) {
        if (onCompleteCallback != null) {
            onCompleteCallback.accept(player);
        }
    }
}
