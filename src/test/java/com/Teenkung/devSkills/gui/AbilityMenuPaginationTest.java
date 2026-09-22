package com.Teenkung.devSkills.gui;

import com.Teenkung.devSkills.util.MenuPages;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class AbilityMenuPaginationTest {

    @Test
    void allSixtyThreeAbilitiesFitAcrossThreePages() {
        Assertions.assertEquals(2, MenuPages.maxPage(63, 28));
        Assertions.assertEquals(2, MenuPages.clampPage(2, 63, 28));
        Assertions.assertEquals(2, MenuPages.clampPage(99, 63, 28));
    }
}
