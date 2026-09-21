package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.ability.ManaAbilityAction;
import com.Teenkung.devSkills.domain.ability.ManaAbilityConfig;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityAction;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class AbilityInfoServiceTest {

    private final AbilityInfoService service = new AbilityInfoService(
            Map.of("lightning_blade", new ManaAbilityConfig(
                    "lightning_blade", "fighting", ManaAbilityAction.LIGHTNING_BLADE,
                    5.0D, 0.0D, 20.0D, 0.0D, 400, 0, 6, 10, 20, 1
            )),
            Map.of(
                    "lumberjack", passive("lumberjack", 6),
                    "forager", passive("forager", 16),
                    "axe_master", passive("axe_master", 26),
                    "valor", passive("valor", 36),
                    "shredder", passive("shredder", 46)
            ),
            null
    );

    @Test
    void abilitiesStayLockedBeforeTheirUnlockLevel() {
        Assertions.assertTrue(service.abilitiesForSkill("fighting", 0, "th").stream().noneMatch(AbilityInfoService.AbilityDisplayEntry::unlocked));
        Assertions.assertTrue(service.abilitiesForSkill("fighting", 5, "th").stream().noneMatch(AbilityInfoService.AbilityDisplayEntry::unlocked));
    }

    @Test
    void abilitiesUnlockAtConfiguredMilestones() {
        assertUnlockedAt(6, 2);
        assertUnlockedAt(16, 3);
        assertUnlockedAt(26, 4);
        assertUnlockedAt(36, 5);
        assertUnlockedAt(46, 6);
    }

    @Test
    void passiveMaxAndManaTimingMatchRuntimeConfiguration() {
        List<AbilityInfoService.AbilityDisplayEntry> entries = service.abilitiesForSkill("fighting", 100, "th");
        AbilityInfoService.AbilityDisplayEntry lumberjack = byId(entries, "lumberjack");
        AbilityInfoService.AbilityDisplayEntry lightningBlade = byId(entries, "lightning_blade");

        Assertions.assertTrue(lumberjack.maxed());
        Assertions.assertEquals(10, lumberjack.abilityLevel());
        Assertions.assertTrue(lightningBlade.detail().contains("คูลดาวน์ 20 วินาที"));
        Assertions.assertTrue(lightningBlade.detail().contains("ระยะเวลา 1 วินาที"));
    }

    @Test
    void passiveDetailsDescribeTheActualListenerEffect() {
        AbilityInfoService.AbilityDisplayEntry lumberjack = byId(service.abilitiesForSkill("fighting", 6, "th"), "lumberjack");

        Assertions.assertEquals(AbilityInfoService.AbilityType.PASSIVE, lumberjack.type());
        Assertions.assertTrue(lumberjack.detail().contains("ได้รับของเพิ่ม 1 ชิ้น"));
    }

    private void assertUnlockedAt(int level, int count) {
        long unlocked = service.abilitiesForSkill("fighting", level, "th").stream()
                .filter(AbilityInfoService.AbilityDisplayEntry::unlocked)
                .count();
        Assertions.assertEquals(count, unlocked);
    }

    private AbilityInfoService.AbilityDisplayEntry byId(List<AbilityInfoService.AbilityDisplayEntry> entries, String id) {
        return entries.stream().filter(entry -> entry.abilityId().equals(id)).findFirst().orElseThrow();
    }

    private PassiveAbilityConfig passive(String id, int unlockLevel) {
        return new PassiveAbilityConfig(
                id,
                "fighting",
                PassiveAbilityAction.WOOD_BONUS_DROP,
                id,
                id,
                2.0D,
                0.0D,
                unlockLevel,
                10,
                10
        );
    }
}
