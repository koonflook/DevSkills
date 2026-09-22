package com.Teenkung.devSkills.domain;

import com.Teenkung.devSkills.domain.ability.PassiveAbilityAction;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class PassiveAbilityConfigTest {

    @Test
    void startsAtUnlockAndCapsAtConfiguredLevel() {
        PassiveAbilityConfig config = new PassiveAbilityConfig(
                "lucky_miner",
                "mining",
                PassiveAbilityAction.MINING_BONUS_DROP,
                "นักขุดผู้โชคดี",
                "Lucky Miner",
                2.0D,
                2.0D,
                6,
                10,
                10
        );

        Assertions.assertEquals(0, config.abilityLevel(5));
        Assertions.assertEquals(1, config.abilityLevel(6));
        Assertions.assertEquals(10, config.abilityLevel(100));
        Assertions.assertEquals(20.0D, config.value(100), 0.0001D);
        Assertions.assertEquals("นักขุดผู้โชคดี", config.displayName("th"));
        Assertions.assertEquals("Lucky Miner", config.displayName("en"));
    }
}
