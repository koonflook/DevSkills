package com.Teenkung.devSkills.listener;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

public final class BlockSourceClassifier {

    private static final Set<String> MATURE_CROPS = Set.of(
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
            "SWEET_BERRY_BUSH", "TORCHFLOWER_CROP", "PITCHER_CROP"
    );
    private static final Set<String> OTHER_HARVESTABLES = Set.of(
            "MELON", "PUMPKIN", "SUGAR_CANE", "CACTUS", "BAMBOO", "KELP", "KELP_PLANT",
            "BROWN_MUSHROOM", "RED_MUSHROOM", "CHORUS_PLANT", "CHORUS_FLOWER",
            "CAVE_VINES", "CAVE_VINES_PLANT"
    );
    private static final Set<String> FORAGING = Set.of(
            "MANGROVE_ROOTS", "MUDDY_MANGROVE_ROOTS", "BAMBOO_BLOCK", "STRIPPED_BAMBOO_BLOCK"
    );
    private static final Set<String> MINING = Set.of(
            "STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "GRANITE", "DIORITE", "ANDESITE",
            "DEEPSLATE", "COBBLED_DEEPSLATE", "NETHERRACK", "BLACKSTONE", "GILDED_BLACKSTONE",
            "BASALT", "SMOOTH_BASALT", "END_STONE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK",
            "POINTED_DRIPSTONE", "OBSIDIAN", "CRYING_OBSIDIAN", "MAGMA_BLOCK", "SANDSTONE",
            "RED_SANDSTONE", "ANCIENT_DEBRIS", "AMETHYST_BLOCK", "BUDDING_AMETHYST",
            "SMALL_AMETHYST_BUD", "MEDIUM_AMETHYST_BUD", "LARGE_AMETHYST_BUD", "AMETHYST_CLUSTER"
    );
    private static final Set<String> EXCAVATION = Set.of(
            "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "GRASS_BLOCK", "DIRT_PATH", "PODZOL", "MYCELIUM",
            "MUD", "PACKED_MUD", "SAND", "RED_SAND", "SUSPICIOUS_SAND", "GRAVEL", "SUSPICIOUS_GRAVEL",
            "CLAY", "SNOW", "SNOW_BLOCK", "POWDER_SNOW", "SOUL_SAND", "SOUL_SOIL"
    );

    private BlockSourceClassifier() {
    }

    public static Optional<String> classify(Material material, boolean mature, boolean nonAgeableFarmingAllowed) {
        Optional<String> named = classifyName(material.name(), mature, nonAgeableFarmingAllowed);
        if (named.isPresent()) {
            return named;
        }
        if (mature && tagged(Tag.CROPS, material)) {
            return Optional.of("farming");
        }
        if (tagged(Tag.LOGS, material) || tagged(Tag.CRIMSON_STEMS, material) || tagged(Tag.WARPED_STEMS, material)) {
            return Optional.of("foraging");
        }
        return Optional.empty();
    }

    public static Optional<String> classifyName(String materialName, boolean mature, boolean nonAgeableFarmingAllowed) {
        String name = materialName.toUpperCase(Locale.ROOT);
        if (mature && MATURE_CROPS.contains(name)) {
            return Optional.of("farming");
        }
        if (nonAgeableFarmingAllowed && OTHER_HARVESTABLES.contains(name)) {
            return Optional.of("farming");
        }
        if (FORAGING.contains(name) || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) {
            return Optional.of("foraging");
        }
        if (MINING.contains(name) || name.endsWith("_ORE") || (name.startsWith("RAW_") && name.endsWith("_BLOCK"))) {
            return Optional.of("mining");
        }
        if (EXCAVATION.contains(name) || name.endsWith("_TERRACOTTA") || name.endsWith("CONCRETE_POWDER")) {
            return Optional.of("excavation");
        }
        return Optional.empty();
    }

    private static boolean tagged(Tag<Material> tag, Material material) {
        return tag != null && tag.isTagged(material);
    }
}
