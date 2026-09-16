package com.Teenkung.devSkills.listener;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlockSourceClassifierTest {

    @Test
    void classifiesEachBlockIntoAtMostOneSkill() {
        Assertions.assertEquals(Optional.of("farming"), BlockSourceClassifier.classifyName("WHEAT", true, true));
        Assertions.assertEquals(Optional.of("foraging"), BlockSourceClassifier.classifyName("OAK_LOG", false, true));
        Assertions.assertEquals(Optional.of("mining"), BlockSourceClassifier.classifyName("DIAMOND_ORE", false, true));
        Assertions.assertEquals(Optional.of("mining"), BlockSourceClassifier.classifyName("AMETHYST_CLUSTER", false, true));
        Assertions.assertEquals(Optional.of("excavation"), BlockSourceClassifier.classifyName("DIRT", false, true));
        Assertions.assertEquals(Optional.empty(), BlockSourceClassifier.classifyName("OAK_PLANKS", false, true));
        Assertions.assertEquals(Optional.empty(), BlockSourceClassifier.classifyName("IRON_DOOR", false, true));
    }

    @Test
    void requiresMaturityForAgeableCrops() {
        Assertions.assertEquals(Optional.empty(), BlockSourceClassifier.classifyName("WHEAT", false, true));
        Assertions.assertEquals(Optional.of("farming"), BlockSourceClassifier.classifyName("WHEAT", true, false));
    }

    @Test
    void nonAgeableHarvestsRespectSetting() {
        Assertions.assertEquals(Optional.empty(), BlockSourceClassifier.classifyName("MELON", false, false));
        Assertions.assertEquals(Optional.of("farming"), BlockSourceClassifier.classifyName("MELON", false, true));
    }
}
