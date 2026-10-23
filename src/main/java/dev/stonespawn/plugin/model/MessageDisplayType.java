package dev.stonespawn.plugin.model;

import java.util.Locale;

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
            return MessageDisplayType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
