package com.Teenkung.devSkills.domain.ability;

public record PassiveAbilityConfig(
        String id,
        String skillId,
        PassiveAbilityAction action,
        String displayNameTh,
        String displayNameEn,
        double baseValue,
        double valuePerLevel,
        int unlockLevel,
        int levelUp,
        int maxAbilityLevel
) {

    public int abilityLevel(int skillLevel) {
        if (skillLevel < unlockLevel) {
            return 0;
        }
        int level = Math.max(1, (skillLevel - unlockLevel) / levelUp + 1);
        return maxAbilityLevel > 0 ? Math.min(level, maxAbilityLevel) : level;
    }

    public double value(int skillLevel) {
        int level = abilityLevel(skillLevel);
        return level <= 0 ? 0.0D : Math.max(0.0D, baseValue + (level - 1) * valuePerLevel);
    }

    public String displayName(String locale) {
        return "th".equalsIgnoreCase(locale) ? displayNameTh : displayNameEn;
    }
}
