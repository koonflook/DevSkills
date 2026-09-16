package com.Teenkung.devSkills.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record HudConfig(
        boolean enabled,
        long refreshTicks,
        String actionBarFormat,
        long externalActionBarHoldTicks,
        long transientActionBarTicks,
        String unresolvedPlaceholder,
        boolean boosterBossBarEnabled,
        long boosterRotationTicks,
        String boosterBossBarFormat,
        BossBar.Color boosterBossBarColor,
        BossBar.Overlay boosterBossBarOverlay
) {

    public static HudConfig load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "hud.yml");
        if (!file.isFile()) {
            plugin.saveResource("hud.yml", false);
        }
        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        try (InputStream input = plugin.getResource("hud.yml")) {
            if (input != null) {
                live.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Unable to layer bundled HUD defaults: " + exception.getMessage());
        }
        return from(live);
    }

    public static HudConfig from(YamlConfiguration yaml) {
        return new HudConfig(
                yaml.getBoolean("enabled", true),
                positive(yaml.getLong("refresh-ticks", 20L), 20L),
                yaml.getString("action-bar.format", "<red>❤ <health>/<max_health> <dark_gray>| <aqua>🛡 <defense> <dark_gray>| <gold>Lv %mmocore_level% <gray>(%mmocore_experience%/%mmocore_next_level%, %mmocore_level_percent%%)"),
                nonNegative(yaml.getLong("action-bar.external-hold-ticks", 60L)),
                positive(yaml.getLong("action-bar.transient-ticks", 40L), 40L),
                yaml.getString("unresolved-placeholder", "N/A"),
                yaml.getBoolean("booster-boss-bar.enabled", true),
                positive(yaml.getLong("booster-boss-bar.rotation-ticks", 60L), 60L),
                yaml.getString("booster-boss-bar.format", "<gold><booster_label> <yellow>x<booster_multiplier> <gray><booster_scope> (<remaining>) <dark_gray>[<index>/<count>] <aqua>Combined x<combined_multiplier>"),
                enumValue(BossBar.Color.class, yaml.getString("booster-boss-bar.color", "PURPLE"), BossBar.Color.PURPLE),
                enumValue(BossBar.Overlay.class, yaml.getString("booster-boss-bar.overlay", "PROGRESS"), BossBar.Overlay.PROGRESS)
        );
    }

    private static long positive(long value, long fallback) {
        return value > 0L ? value : fallback;
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
        try {
            return Enum.valueOf(type, raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
