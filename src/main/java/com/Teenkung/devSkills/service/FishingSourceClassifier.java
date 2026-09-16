package com.Teenkung.devSkills.service;

import java.util.Locale;
import java.util.Set;

public final class FishingSourceClassifier {

    private static final Set<String> FISH = Set.of(
            "COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH"
    );
    private static final Set<String> TREASURE = Set.of(
            "BOW", "ENCHANTED_BOOK", "FISHING_ROD", "NAME_TAG", "NAUTILUS_SHELL", "SADDLE"
    );
    private static final Set<String> JUNK = Set.of(
            "LILY_PAD", "BOWL", "LEATHER", "LEATHER_BOOTS", "ROTTEN_FLESH", "STICK", "STRING",
            "POTION", "BONE", "INK_SAC", "TRIPWIRE_HOOK", "BAMBOO"
    );

    private FishingSourceClassifier() {
    }

    public static String group(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return "DEFAULT";
        }
        String normalized = materialName.toUpperCase(Locale.ROOT);
        if (FISH.contains(normalized)) {
            return "FISH";
        }
        if (TREASURE.contains(normalized)) {
            return "TREASURE";
        }
        if (JUNK.contains(normalized)) {
            return "JUNK";
        }
        return "DEFAULT";
    }
}
