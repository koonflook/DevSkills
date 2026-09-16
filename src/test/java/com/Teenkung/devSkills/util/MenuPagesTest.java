package com.Teenkung.devSkills.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class MenuPagesTest {

    @Test
    void clampsPagedSkillRoads() {
        Assertions.assertEquals(3, MenuPages.maxPage(100, 28));
        Assertions.assertEquals(3, MenuPages.clampPage(99, 100, 28));
        Assertions.assertEquals(0, MenuPages.clampPage(-4, 100, 28));
        Assertions.assertEquals(57, MenuPages.levelAt(2, 0, 28));
    }
}
