package com.Teenkung.devSkills.domain;

import com.Teenkung.devSkills.domain.skill.RewardTable;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import com.Teenkung.devSkills.domain.skill.RewardPattern;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class RewardTableTest {

    @Test
    void combinesPatternAndExplicitRewards() {
        TraitReward patternReward = new TraitReward("strength", 1);
        TraitReward explicitReward = new TraitReward("health", 2);
        RewardTable table = new RewardTable(
                List.of(new RewardPattern(2, 0, patternReward)),
                Map.of(4, List.of(explicitReward))
        );
        Assertions.assertEquals(0, table.rewardsForLevel(3).size());
        Assertions.assertEquals(2, table.rewardsForLevel(4).size());
    }
}
