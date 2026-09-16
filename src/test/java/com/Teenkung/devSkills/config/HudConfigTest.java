package com.Teenkung.devSkills.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class HudConfigTest {

    @Test
    void invalidIntervalsAndEnumsUseSafeDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("refresh-ticks", 0L);
        yaml.set("action-bar.external-hold-ticks", -1L);
        yaml.set("booster-boss-bar.rotation-ticks", -4L);
        yaml.set("booster-boss-bar.color", "not-a-color");

        HudConfig config = HudConfig.from(yaml);

        Assertions.assertEquals(20L, config.refreshTicks());
        Assertions.assertEquals(0L, config.externalActionBarHoldTicks());
        Assertions.assertEquals(60L, config.boosterRotationTicks());
        Assertions.assertEquals(BossBar.Color.PURPLE, config.boosterBossBarColor());
    }

    @Test
    void configuredValuesAreRetained() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("refresh-ticks", 10L);
        yaml.set("booster-boss-bar.color", "blue");
        yaml.set("booster-boss-bar.overlay", "notched_10");

        HudConfig config = HudConfig.from(yaml);

        Assertions.assertEquals(10L, config.refreshTicks());
        Assertions.assertEquals(BossBar.Color.BLUE, config.boosterBossBarColor());
        Assertions.assertEquals(BossBar.Overlay.NOTCHED_10, config.boosterBossBarOverlay());
    }
}
