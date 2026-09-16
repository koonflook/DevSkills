package com.Teenkung.devSkills.config;

import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class UiConfigTest {

    @Test
    void localizedTextFallsBackAndSubstitutesValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("text.en.navigation.back", "Back <name>");
        yaml.set("text.th.navigation.back", "Thai <name>");
        UiConfig config = UiConfig.from(yaml);

        Assertions.assertEquals("Thai Alex", config.text("th_TH", "navigation.back", Map.of("name", "Alex")));
        Assertions.assertEquals("Back Alex", config.text("de_DE", "navigation.back", Map.of("name", "Alex")));
    }

    @Test
    void rendererPageSizeAndImagesAreValidated() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("java.renderer", "dialog");
        yaml.set("bedrock.page-size", 0);
        yaml.set("bedrock.images.skills.mining", "https://example.com/mining.png");
        UiConfig config = UiConfig.from(yaml);

        Assertions.assertEquals(UiConfig.JavaRenderer.DIALOG, config.javaRenderer());
        Assertions.assertTrue(config.dialogMenusEnabled());
        Assertions.assertEquals(1, config.bedrockPageSize());
        Assertions.assertEquals("https://example.com/mining.png", config.bedrockImage("skills.mining"));
    }

    @Test
    void inventoryIsDefaultAndAutoDoesNotTurnEveryMenuIntoADialog() {
        UiConfig defaults = UiConfig.from(new YamlConfiguration());
        Assertions.assertEquals(UiConfig.JavaRenderer.INVENTORY, defaults.javaRenderer());
        Assertions.assertFalse(defaults.dialogMenusEnabled());

        YamlConfiguration autoYaml = new YamlConfiguration();
        autoYaml.set("java.renderer", "AUTO");
        UiConfig auto = UiConfig.from(autoYaml);
        Assertions.assertEquals(UiConfig.JavaRenderer.AUTO, auto.javaRenderer());
        Assertions.assertFalse(auto.dialogMenusEnabled());

        YamlConfiguration invalidYaml = new YamlConfiguration();
        invalidYaml.set("java.renderer", "not-a-renderer");
        UiConfig invalid = UiConfig.from(invalidYaml);
        Assertions.assertEquals(UiConfig.JavaRenderer.INVENTORY, invalid.javaRenderer());
        Assertions.assertFalse(invalid.dialogMenusEnabled());
    }
}
