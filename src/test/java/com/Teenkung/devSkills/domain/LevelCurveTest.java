package com.Teenkung.devSkills.domain;

import com.Teenkung.devSkills.domain.skill.LevelCurve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class LevelCurveTest {

    @Test
    void calculatesLevelsFromCachedThresholds() {
        LevelCurve curve = new LevelCurve("100 * level", 10);
        Assertions.assertEquals(1, curve.levelForXp(0.0D, 10));
        Assertions.assertEquals(2, curve.levelForXp(100.0D, 10));
        Assertions.assertEquals(3, curve.levelForXp(300.0D, 10));
        Assertions.assertEquals(100.0D, curve.xpToNext(1), 0.001D);
    }

    @Test
    void rejectsCumulativeOverflow() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new LevelCurve("1.0E308", 3));
    }
}
