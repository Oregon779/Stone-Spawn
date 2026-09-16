package dev.stonespawn.plugin.model;

public enum JoinMode {
    ALWAYS,
    FIRST_JOIN,
    NEVER;

    public static JoinMode fromConfig(String value, JoinMode fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return JoinMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
