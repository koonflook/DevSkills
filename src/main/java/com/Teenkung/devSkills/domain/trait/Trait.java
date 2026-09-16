package com.Teenkung.devSkills.domain.trait;

import com.Teenkung.devSkills.config.IconConfig;
import java.util.List;
import org.bukkit.Material;

public record Trait(
        String id,
        String displayName,
        IconConfig icon,
        int baseLevel,
        Integer maxLevel,
        double xpMultiplierPerLevel,
        List<StatMapping> stats
) {
    public Trait(String id, String displayName, int baseLevel, Integer maxLevel, double xpMultiplierPerLevel, List<StatMapping> stats) {
        this(id, displayName, IconConfig.of(Material.NETHER_STAR), baseLevel, maxLevel, xpMultiplierPerLevel, stats);
    }

    public int clamp(int level) {
        int atLeastZero = Math.max(0, level);
        if (maxLevel == null) {
            return atLeastZero;
        }
        return Math.min(maxLevel, atLeastZero);
    }
}
