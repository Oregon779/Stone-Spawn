package dev.stonespawn.plugin.model;

import java.util.Locale;

public enum WorldMode {
    NONE,
    WHITELIST,
    BLACKLIST;

    public static WorldMode fromConfig(String value, WorldMode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return WorldMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
