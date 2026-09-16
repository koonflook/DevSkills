package com.Teenkung.devSkills.util;

import com.Teenkung.devSkills.domain.skill.CommandReward;
import com.Teenkung.devSkills.domain.skill.ItemReward;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import com.Teenkung.devSkills.domain.trait.Trait;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class RewardDisplayTest {

    @Test
    void usesDisplayNamesForTraitAndCommandRewards() {
        Map<String, Trait> traits = Map.of(
                "crit_chance",
                new Trait("crit_chance", "Critical Chance", 0, 100, 0.0D, List.of())
        );

        Assertions.assertEquals("1x Critical Chance", RewardDisplay.label(new TraitReward("crit_chance", 1), traits));
        Assertions.assertEquals("1x Diamond", RewardDisplay.label(new CommandReward("give <player> diamond 1", "Diamond", 1), traits));
    }

    @Test
    void fallsBackToHumanizedMaterialNamesForItemRewards() {
        Assertions.assertEquals("2x Diamond", RewardDisplay.label(new ItemReward(Material.DIAMOND, 2, "", List.of()), Map.of()));
    }
}
