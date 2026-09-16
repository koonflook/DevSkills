package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.skill.Reward;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import com.Teenkung.devSkills.domain.trait.EffectiveTraits;
import com.Teenkung.devSkills.domain.trait.ModifierKind;
import com.Teenkung.devSkills.domain.trait.StatMapping;
import com.Teenkung.devSkills.domain.trait.Trait;
import com.Teenkung.devSkills.domain.user.UserProfile;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TraitService {

    private final Map<String, Trait> traits;
    private final Map<String, Skill> skills;
    private final LevelerService levelerService;

    public TraitService(Map<String, Trait> traits, Map<String, Skill> skills, LevelerService levelerService) {
        this.traits = traits;
        this.skills = skills;
        this.levelerService = levelerService;
    }

    public EffectiveTraits effectiveTraits(UserProfile profile) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Trait trait : traits.values()) {
            levels.put(trait.id(), trait.baseLevel());
        }
        for (Skill skill : skills.values()) {
            int level = levelerService.level(skill.id(), profile.xp(skill.id()));
            for (int current = 1; current <= level; current++) {
                for (Reward reward : skill.rewards().rewardsForLevel(current)) {
                    if (reward instanceof TraitReward traitReward) {
                        levels.merge(traitReward.traitId(), traitReward.amount(), Integer::sum);
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> entry : profile.manualTraitLevels().entrySet()) {
            levels.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        for (Trait trait : traits.values()) {
            levels.put(trait.id(), trait.clamp(levels.getOrDefault(trait.id(), 0)));
        }
        return new EffectiveTraits(levels, statTotals(levels));
    }

    public Map<String, Trait> traits() {
        return traits;
    }

    public int traitLevel(UserProfile profile, String traitId) {
        return effectiveTraits(profile).level(traitId);
    }

    public double xpMultiplier(UserProfile profile) {
        EffectiveTraits effectiveTraits = effectiveTraits(profile);
        double multiplier = 1.0D;
        for (Trait trait : traits.values()) {
            if (trait.xpMultiplierPerLevel() > 0.0D) {
                multiplier += effectiveTraits.level(trait.id()) * trait.xpMultiplierPerLevel();
            }
        }
        return multiplier;
    }

    private Map<ModifierKind, Map<String, Double>> statTotals(Map<String, Integer> levels) {
        Map<ModifierKind, Map<String, Double>> totals = new EnumMap<>(ModifierKind.class);
        for (Trait trait : traits.values()) {
            int level = levels.getOrDefault(trait.id(), 0);
            if (level == 0) {
                continue;
            }
            for (StatMapping mapping : trait.stats()) {
                totals
                        .computeIfAbsent(mapping.kind(), ignored -> new LinkedHashMap<>())
                        .merge(mapping.stat(), mapping.perLevel() * level, Double::sum);
            }
        }
        return totals;
    }
}
