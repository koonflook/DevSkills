package com.Teenkung.devSkills.domain.skill;

import com.Teenkung.devSkills.config.IconConfig;
import org.bukkit.Material;

public record Skill(String id, String displayName, IconConfig icon, int maxLevel, LevelCurve curve, RewardTable rewards) {

    public Skill(String id, String displayName, int maxLevel, LevelCurve curve, RewardTable rewards) {
        this(id, displayName, IconConfig.of(Material.EXPERIENCE_BOTTLE), maxLevel, curve, rewards);
    }
}
