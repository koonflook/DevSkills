package com.Teenkung.devSkills.config;

import org.bukkit.Material;

public record IconConfig(
        Material material,
        Integer customModelData,
        CustomModelDataConfig customModelDataComponent
) {

    public IconConfig {
        if (material == null) {
            material = Material.STONE;
        }
        if (customModelDataComponent == null) {
            customModelDataComponent = CustomModelDataConfig.emptyConfig();
        }
    }

    public static IconConfig of(Material material) {
        return new IconConfig(material, null, CustomModelDataConfig.emptyConfig());
    }

    public boolean hasCustomModelDataComponent() {
        return !customModelDataComponent.empty();
    }
}
