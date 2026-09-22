package com.Teenkung.devSkills.domain;

import com.Teenkung.devSkills.domain.ability.ManaAbilityAction;
import com.Teenkung.devSkills.domain.ability.ManaAbilityConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class ManaAbilityConfigTest {

    @Test
    void scalesValueCostAndCooldownFromUnlockedLevels() {
        ManaAbilityConfig config = new ManaAbilityConfig(
                "charged_shot",
                "archery",
                ManaAbilityAction.CHARGED_SHOT,
                0.5D,
                0.1D,
                10.0D,
                5.0D,
                200,
                -5,
                6,
                6,
                100,
                1
        );

        Assertions.assertEquals(0, config.abilityLevel(5));
        Assertions.assertEquals(1, config.abilityLevel(6));
        Assertions.assertEquals(2, config.abilityLevel(12));
        Assertions.assertEquals(0.6D, config.value(12), 0.0001D);
        Assertions.assertEquals(15.0D, config.cost(12), 0.0001D);
        Assertions.assertEquals(195L, config.cooldownTicks(12));
    }

    @Test
    void keepsLightningBladeCooldownAtTwentySeconds() {
        ManaAbilityConfig config = new ManaAbilityConfig(
                "lightning_blade",
                "fighting",
                ManaAbilityAction.LIGHTNING_BLADE,
                5.0D,
                5.0D,
                20.0D,
                5.0D,
                400,
                0,
                6,
                6,
                20,
                1
        );

        Assertions.assertEquals(400L, config.cooldownTicks(100));
        Assertions.assertEquals(20, config.durationTicks());
    }
}
