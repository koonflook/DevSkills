package com.Teenkung.devSkills.config;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class ConfigManagerMenuDefaultsTest {

    @Test
    void bundledMenuDefaultsAddMissingValuesWithoutReplacingCustomValues() {
        YamlConfiguration live = new YamlConfiguration();
        live.set("items.unlocked-abilities.slot", 12);
        live.set("templates.skill.lore", List.of("<gray>custom lore"));

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.unlocked-abilities.slot", 43);
        defaults.set("templates.skill.lore", List.of("<ability_summary_lines>"));
        defaults.set("templates.locked-passive.icon.material", "GRAY_DYE");
        defaults.set("templates.unlocked-mana.icon.material", "MAGENTA_DYE");

        ConfigManager.mergeMenuDefaults(live, defaults);

        Assertions.assertEquals(12, live.getInt("items.unlocked-abilities.slot"));
        Assertions.assertEquals(List.of("<gray>custom lore"), live.getStringList("templates.skill.lore"));
        Assertions.assertEquals("GRAY_DYE", live.getString("templates.locked-passive.icon.material"));
        Assertions.assertEquals("MAGENTA_DYE", live.getString("templates.unlocked-mana.icon.material"));
    }
}
