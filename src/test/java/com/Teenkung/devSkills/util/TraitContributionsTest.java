package com.Teenkung.devSkills.util;

import com.Teenkung.devSkills.domain.skill.LevelCurve;
import com.Teenkung.devSkills.domain.skill.RewardPattern;
import com.Teenkung.devSkills.domain.skill.RewardTable;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class TraitContributionsTest {

    @Test
    void totalsTraitRewardsUnlockedBySkillLevel() {
        Skill skill = new Skill(
                "farming",
                "Farming",
                10,
                new LevelCurve("10 * level", 10),
                new RewardTable(
                        List.of(new RewardPattern(2, 0, new TraitReward("luck", 1))),
                        Map.of(5, List.of(new TraitReward("luck", 3)))
                )
        );

        Assertions.assertTrue(TraitContributions.feedsTrait(skill, "luck"));
        Assertions.assertEquals(2, TraitContributions.fromSkill(skill, "luck", 4));
        Assertions.assertEquals(5, TraitContributions.fromSkill(skill, "luck", 5));
    }
}
