package dev.stonespawn.plugin.model;

public enum MessageDisplayType {
    CHAT,
    ACTIONBAR,
    BOSSBAR,
    TITLE;

    public static MessageDisplayType fromConfig(String value, MessageDisplayType fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return MessageDisplayType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
