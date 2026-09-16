package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.source.SourceCategory;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SourceCoverageAuditTest {

    @Test
    void bundledPresetCoversEveryListenerRouteAndFallback() {
        InputStream resource = SourceCoverageAuditTest.class.getClassLoader().getResourceAsStream("sources.yml");
        Assertions.assertNotNull(resource);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8)
        );

        Assertions.assertEquals(java.util.List.of(), SourceCoverageAudit.issues(loadSources(yaml)));
    }

    @Test
    void reportsConfiguredSkillsWithoutAListenerRoute() {
        Map<String, Map<SourceCategory, Map<String, Double>>> sources = new LinkedHashMap<>();
        sources.put("unknown", Map.of(SourceCategory.BLOCK, Map.of("DEFAULT", 1.0D)));

        Assertions.assertTrue(SourceCoverageAudit.issues(sources).contains("No listener route for skill unknown"));
    }

    private Map<String, Map<SourceCategory, Map<String, Double>>> loadSources(YamlConfiguration yaml) {
        Map<String, Map<SourceCategory, Map<String, Double>>> loaded = new LinkedHashMap<>();
        for (String skillId : yaml.getKeys(false)) {
            ConfigurationSection skill = yaml.getConfigurationSection(skillId);
            Map<SourceCategory, Map<String, Double>> categories = new LinkedHashMap<>();
            for (String categoryKey : skill.getKeys(false)) {
                ConfigurationSection category = skill.getConfigurationSection(categoryKey);
                Map<String, Double> values = new LinkedHashMap<>();
                for (String key : category.getKeys(false)) {
                    values.put(key.toUpperCase(Locale.ROOT), category.getDouble(key));
                }
                categories.put(SourceCategory.valueOf(categoryKey.toUpperCase(Locale.ROOT)), Map.copyOf(values));
            }
            loaded.put(skillId, Map.copyOf(categories));
        }
        return Map.copyOf(loaded);
    }
}
