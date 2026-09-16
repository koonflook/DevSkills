package com.Teenkung.devSkills.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FishingSourceClassifierTest {

    @Test
    void mapsVanillaFishingLootGroups() {
        Assertions.assertEquals("FISH", FishingSourceClassifier.group("cod"));
        Assertions.assertEquals("TREASURE", FishingSourceClassifier.group("ENCHANTED_BOOK"));
        Assertions.assertEquals("JUNK", FishingSourceClassifier.group("LILY_PAD"));
        Assertions.assertEquals("DEFAULT", FishingSourceClassifier.group("DIAMOND"));
        Assertions.assertEquals("DEFAULT", FishingSourceClassifier.group(null));
    }
}
