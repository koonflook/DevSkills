package com.Teenkung.devSkills.config;

import java.util.List;

public record MenuTemplateConfig(
        List<Integer> slots,
        IconConfig icon,
        String name,
        List<String> lore,
        String action
) {

    public MenuTemplateConfig {
        slots = List.copyOf(slots);
        lore = List.copyOf(lore);
    }
}
