package com.Teenkung.devSkills.util;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class AgilityMovementTest {

    @Test
    void allowsOnlySprintingOrSwimmingWithoutFlightStates() {
        Assertions.assertTrue(AgilityMovement.eligible(true, false, false, false, false, false, GameMode.SURVIVAL, true));
        Assertions.assertTrue(AgilityMovement.eligible(false, true, false, false, false, false, GameMode.SURVIVAL, true));
        Assertions.assertFalse(AgilityMovement.eligible(false, false, false, false, false, false, GameMode.SURVIVAL, true));
    }

    @Test
    void blocksFlyingAndOtherMovementAbuseStates() {
        Assertions.assertFalse(AgilityMovement.eligible(true, false, true, false, false, false, GameMode.SURVIVAL, true));
        Assertions.assertFalse(AgilityMovement.eligible(true, false, false, true, false, false, GameMode.SURVIVAL, true));
        Assertions.assertFalse(AgilityMovement.eligible(false, true, false, false, true, false, GameMode.SURVIVAL, true));
        Assertions.assertFalse(AgilityMovement.eligible(true, false, false, false, false, true, GameMode.SURVIVAL, true));
        Assertions.assertFalse(AgilityMovement.eligible(true, false, false, false, false, false, GameMode.SPECTATOR, true));
        Assertions.assertFalse(AgilityMovement.eligible(true, false, false, false, false, false, GameMode.CREATIVE, true));
    }
}
