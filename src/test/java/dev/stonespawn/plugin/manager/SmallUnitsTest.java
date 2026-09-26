package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.PluginTestBase;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallUnitsTest extends PluginTestBase {

    @Test
    void versionComparison() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("2.0", "1.99.99"));
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.2.0-beta", "1.2.0"));
        assertFalse(UpdateChecker.isNewer("garbage", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("99999999999999999999", "1.0.0"));
    }

    @Test
    void cooldownSurvivesQuitButExpiredEntriesArePruned() {
        CooldownManager cooldowns = new CooldownManager();
        UUID active = UUID.randomUUID();
        cooldowns.setUsed(active);
        cooldowns.handleQuit(active, 10);
        assertTrue(cooldowns.getRemaining(active, 10) > 0);

        UUID expired = UUID.randomUUID();
        cooldowns.setUsed(expired);
        cooldowns.handleQuit(expired, 0);
        assertEquals(0, cooldowns.getRemaining(expired, 10), "expired entries are removed on quit");

        for (int i = 0; i < 300; i++) {
            cooldowns.setUsed(UUID.randomUUID());
        }
        cooldowns.handleQuit(UUID.randomUUID(), 0);
        assertEquals(0, cooldowns.size(), "map is pruned once it grows");
    }

    @Test
    void sanitizeInput() {
        assertEquals("hub_1-a.b", MessageManager.sanitizeInput("hub_1-a.b"));
        assertEquals("?red?x", MessageManager.sanitizeInput("<red>x"));
        assertEquals("?4x", MessageManager.sanitizeInput("&4x"));
        assertEquals("x".repeat(32) + "...", MessageManager.sanitizeInput("x".repeat(500)));
    }

    @Test
    void invalidSoundsAndDataParticlesFallBackAndWarnOnlyOnce() {
        List<LogRecord> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        plugin.getLogger().addHandler(handler);
        EffectManager effects = plugin.getEffectManager();

        for (int i = 0; i < 100; i++) {
            assertEquals(Sound.UI_BUTTON_CLICK, effects.parseSound("NOT_A_SOUND", Sound.UI_BUTTON_CLICK));
            assertEquals(Particle.PORTAL, effects.parseParticle("DUST", Particle.PORTAL));
        }
        assertEquals(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, effects.parseSound("entity_firework_rocket_launch", Sound.UI_BUTTON_CLICK));
        assertEquals(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, effects.parseSound("minecraft:entity.firework_rocket.launch", Sound.UI_BUTTON_CLICK));
        assertEquals(2, warnings.size(), "one warning per invalid value, not per use");
        plugin.getLogger().removeHandler(handler);
    }
}
