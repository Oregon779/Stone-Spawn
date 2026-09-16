package dev.stonespawn.plugin.model;

public enum WorldMode {
    NONE,
    WHITELIST,
    BLACKLIST;

    public static WorldMode fromConfig(String value, WorldMode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return WorldMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
