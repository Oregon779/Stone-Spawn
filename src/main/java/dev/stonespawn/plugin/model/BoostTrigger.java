package dev.stonespawn.plugin.model;

import java.util.Locale;

public enum BoostTrigger {
    SWAP_HAND,
    SNEAK,
    LEFT_CLICK;

    public static BoostTrigger fromConfig(String value, BoostTrigger fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return BoostTrigger.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public String messageKey() {
        return "elytra.keys." + keyName();
    }

    public String bedrockMessageKey() {
        return "elytra.bedrock-keys." + keyName();
    }

    private String keyName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
