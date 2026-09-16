package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.skill.Skill;
import java.util.Map;

public final class LevelerService {

    private final Map<String, Skill> skills;

    public LevelerService(Map<String, Skill> skills) {
        this.skills = skills;
    }

    public int level(String skillId, double xp) {
        Skill skill = skills.get(skillId);
        if (skill == null) {
            return 1;
        }
        return skill.curve().levelForXp(xp, skill.maxLevel());
    }

    public double xpForLevel(String skillId, int level) {
        Skill skill = skills.get(skillId);
        if (skill == null) {
            return 0.0D;
        }
        int bounded = Math.max(1, Math.min(skill.maxLevel(), level));
        return skill.curve().cumulativeForLevel(bounded);
    }

    public double xpIntoLevel(String skillId, double xp) {
        int level = level(skillId, xp);
        return Math.max(0.0D, xp - xpForLevel(skillId, level));
    }

    public double xpRequiredForCurrentLevel(String skillId, double xp) {
        Skill skill = skills.get(skillId);
        if (skill == null) {
            return 0.0D;
        }
        int level = level(skillId, xp);
        return skill.curve().xpToNext(level);
    }

    public double progressPercent(String skillId, double xp) {
        double required = xpRequiredForCurrentLevel(skillId, xp);
        if (!Double.isFinite(required) || required <= 0.0D) {
            return 100.0D;
        }
        return Math.min(100.0D, xpIntoLevel(skillId, xp) / required * 100.0D);
    }
}
