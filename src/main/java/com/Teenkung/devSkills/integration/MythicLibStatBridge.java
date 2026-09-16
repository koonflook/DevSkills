package com.Teenkung.devSkills.integration;

import com.Teenkung.devSkills.config.ConfigManager;
import com.Teenkung.devSkills.domain.trait.EffectiveTraits;
import com.Teenkung.devSkills.domain.trait.ModifierKind;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MythicLibStatBridge {

    private static final String MODIFIER_KEY_PREFIX = "DevSkills";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public MythicLibStatBridge(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void refresh(Player player, EffectiveTraits traits) {
        try {
            MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
            StatMap statMap = data.getStatMap();
            clear(statMap);
            for (Map.Entry<ModifierKind, Map<String, Double>> kindEntry : traits.statTotals().entrySet()) {
                ModifierType modifierType = toMythicType(kindEntry.getKey());
                for (Map.Entry<String, Double> statEntry : kindEntry.getValue().entrySet()) {
                    apply(statMap, data, statEntry.getKey(), statEntry.getValue(), modifierType);
                }
            }
            flush(statMap);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Failed to refresh MythicLib stats for " + player.getName() + ": " + exception.getMessage());
        }
    }

    public void clear(Player player) {
        try {
            MMOPlayerData data = MMOPlayerData.get(player.getUniqueId());
            StatMap statMap = data.getStatMap();
            clear(statMap);
            flush(statMap);
        } catch (RuntimeException | LinkageError exception) {
            if (configManager.settings().debug()) {
                plugin.getLogger().warning("Failed to clear MythicLib stats for " + player.getName() + ": " + exception.getMessage());
            }
        }
    }

    public double stat(Player player, String stat) {
        try {
            double value = MMOPlayerData.get(player.getUniqueId()).getStatMap().getInstance(stat).getFinal();
            return Double.isFinite(value) ? value : 0.0D;
        } catch (RuntimeException | LinkageError exception) {
            if (configManager.settings().debug()) {
                plugin.getLogger().warning("Failed to read MythicLib stat '" + stat + "' for " + player.getName() + ": " + exception.getMessage());
            }
            return 0.0D;
        }
    }

    private void apply(StatMap statMap, MMOPlayerData data, String stat, double value, ModifierType type) {
        if (Math.abs(value) <= 0.0000001D) {
            return;
        }
        try {
            StatInstance instance = statMap.getInstance(stat);
            StatModifier modifier = new StatModifier(MODIFIER_KEY_PREFIX + ":" + type.name() + ":" + stat, stat, value, type);
            instance.registerModifier(modifier);
            statMap.update(stat);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Skipping MythicLib stat '" + stat + "': " + exception.getMessage());
            if (configManager.settings().debug()) {
                plugin.getLogger().fine("MythicLib data was " + data.getUniqueId());
            }
        }
    }

    private void clear(StatMap statMap) {
        for (StatInstance instance : statMap.getInstances()) {
            instance.removeIf(key -> key.startsWith(MODIFIER_KEY_PREFIX));
            instance.update();
        }
    }

    private void flush(StatMap statMap) {
        try {
            Method method = statMap.getClass().getMethod("flushCache");
            method.invoke(statMap);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Older compatible MythicLib runtimes do not expose flushCache; per-stat updates above are enough.
        }
    }

    private ModifierType toMythicType(ModifierKind kind) {
        return switch (kind) {
            case RELATIVE -> ModifierType.RELATIVE;
            case ADDITIVE_MULTIPLIER -> ModifierType.ADDITIVE_MULTIPLIER;
            case FLAT -> ModifierType.FLAT;
        };
    }
}
