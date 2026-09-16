package com.Teenkung.devSkills.config;

import java.util.Map;

public record MenuConfig(
        String title,
        int size,
        MenuItemConfig fill,
        Map<String, MenuItemConfig> items,
        Map<String, MenuTemplateConfig> templates
) {

    public MenuConfig {
        items = Map.copyOf(items);
        templates = Map.copyOf(templates);
    }

    public MenuItemConfig item(String id) {
        return items.get(id);
    }

    public MenuTemplateConfig template(String id) {
        return templates.get(id);
    }
}
