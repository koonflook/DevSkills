package com.Teenkung.devSkills.domain;

import com.Teenkung.devSkills.domain.trait.ModifierKind;
import com.Teenkung.devSkills.domain.trait.StatMapping;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class StatMappingTest {

    @Test
    void humanizesDisplayNameWhenNotConfigured() {
        StatMapping mapping = new StatMapping("CRITICAL_STRIKE_CHANCE", 0.5D, ModifierKind.FLAT);

        Assertions.assertEquals("Critical Strike Chance", mapping.displayName());
    }
}
