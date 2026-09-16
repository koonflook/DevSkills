package com.Teenkung.devSkills.util;

import com.Teenkung.devSkills.domain.skill.Reward;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import java.util.List;

public final class TraitContributions {

    private TraitContributions() {
    }

    public static int fromSkill(Skill skill, String traitId, int skillLevel) {
        int contribution = 0;
        for (int level = 1; level <= Math.min(skillLevel, skill.maxLevel()); level++) {
            contribution += fromRewards(skill.rewards().rewardsForLevel(level), traitId);
        }
        return contribution;
    }

    public static boolean feedsTrait(Skill skill, String traitId) {
        for (var pattern : skill.rewards().patterns()) {
            if (pattern.reward() instanceof TraitReward traitReward && traitReward.traitId().equals(traitId)) {
                return true;
            }
        }
        for (List<Reward> rewards : skill.rewards().levels().values()) {
            if (fromRewards(rewards, traitId) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int fromRewards(List<Reward> rewards, String traitId) {
        int contribution = 0;
        for (Reward reward : rewards) {
            if (reward instanceof TraitReward traitReward && traitReward.traitId().equals(traitId)) {
                contribution += traitReward.amount();
            }
        }
        return contribution;
    }
}
