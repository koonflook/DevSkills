package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.source.SourceCategory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SourceInfoServiceTest {

    @Test
    void archeryUsesConfiguredPerEntityValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("DEFAULT", 2.0D);
        values.put("ZOMBIE", 2.0D);
        values.put("CREEPER", 3.0D);
        values.put("WARDEN", 8.0D);
        Map<SourceCategory, Map<String, Double>> categories = new LinkedHashMap<>();
        categories.put(SourceCategory.ENTITY, values);
        SourceInfoService service = new SourceInfoService(Map.of(
                "archery", categories
        ));

        List<SourceInfoService.SourceEntry> entries = service.entries("archery");

        Assertions.assertEquals(List.of("DEFAULT", "ZOMBIE", "CREEPER", "WARDEN"), entries.stream()
                .map(SourceInfoService.SourceEntry::key)
                .toList());
        Assertions.assertEquals(SourceCategory.ENTITY, entries.getFirst().category());
        Assertions.assertEquals("DEFAULT", entries.getFirst().key());
        Assertions.assertEquals(2.0D, entries.getFirst().amount());
        Assertions.assertEquals("source.behavior.archery", entries.getFirst().behaviorKey());
        Assertions.assertEquals("source.formula.default", entries.getFirst().formulaKey());
        Assertions.assertEquals(8.0D, entries.getLast().amount());
        Assertions.assertTrue(entries.stream().allMatch(entry -> entry.amount() >= 2.0D && entry.amount() <= 8.0D));
    }

    @Test
    void preservesSpecificAndDefaultEntries() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("DEFAULT", 1.5D);
        values.put("ZOMBIE", 2.0D);
        Map<SourceCategory, Map<String, Double>> categories = new LinkedHashMap<>();
        categories.put(SourceCategory.ENTITY, values);
        SourceInfoService service = new SourceInfoService(Map.of(
                "fighting", categories
        ));

        List<SourceInfoService.SourceEntry> entries = service.entries("fighting");

        Assertions.assertEquals(List.of("DEFAULT", "ZOMBIE"), entries.stream().map(SourceInfoService.SourceEntry::key).toList());
        Assertions.assertEquals(List.of(1.5D, 2.0D), entries.stream().map(SourceInfoService.SourceEntry::amount).toList());
    }

    @Test
    void usesSpecialFormulaForScaledSources() {
        SourceInfoService service = new SourceInfoService(Map.of(
                "enchanting", Map.of(SourceCategory.ENCHANT, Map.of("DEFAULT", 2.0D)),
                "agility", Map.of(SourceCategory.MOVEMENT, Map.of("DEFAULT", 1.0D))
        ));

        Assertions.assertEquals("source.formula.enchanting", service.entries("enchanting").getFirst().formulaKey());
        Assertions.assertEquals("source.formula.agility", service.entries("agility").getFirst().formulaKey());
    }

    @Test
    void omitsInvalidConfiguredAmounts() {
        SourceInfoService service = new SourceInfoService(Map.of(
                "archery", Map.of(SourceCategory.ENTITY, Map.of("DEFAULT", 0.0D, "ZOMBIE", 2.0D))
        ));

        Assertions.assertEquals(List.of("ZOMBIE"), service.entries("archery").stream()
                .map(SourceInfoService.SourceEntry::key)
                .toList());
    }
}
