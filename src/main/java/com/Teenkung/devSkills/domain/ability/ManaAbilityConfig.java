package com.Teenkung.devSkills.domain.ability;

public record ManaAbilityConfig(
        String id,
        String skillId,
        ManaAbilityAction action,
        double baseValue,
        double valuePerLevel,
        double baseCost,
        double costPerLevel,
        int baseCooldownTicks,
        int cooldownPerLevel,
        int unlockLevel,
        int levelUp,
        int durationTicks,
        int maxBlocks
) {

    public int abilityLevel(int skillLevel) {
        if (skillLevel < unlockLevel) {
            return 0;
        }
        return Math.max(1, (skillLevel - unlockLevel) / levelUp + 1);
    }

    public double cost(int skillLevel) {
        int level = abilityLevel(skillLevel);
        return Math.max(0.0D, baseCost + Math.max(0, level - 1) * costPerLevel);
    }

    public double value(int skillLevel) {
        int level = abilityLevel(skillLevel);
        return Math.max(0.0D, baseValue + Math.max(0, level - 1) * valuePerLevel);
    }

    public long cooldownTicks(int skillLevel) {
        int level = abilityLevel(skillLevel);
        return Math.max(0L, baseCooldownTicks + (long) Math.max(0, level - 1) * cooldownPerLevel);
    }
}
