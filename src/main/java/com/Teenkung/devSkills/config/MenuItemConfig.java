package com.Teenkung.devSkills.config;

import java.util.List;

public record MenuItemConfig(
        int slot,
        IconConfig icon,
        String name,
        List<String> lore,
        String action
) {

    public MenuItemConfig {
        lore = List.copyOf(lore);
    }
}
