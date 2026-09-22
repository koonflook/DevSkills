package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.source.SourceCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SourceInfoService {

    private final Map<String, Map<SourceCategory, Map<String, Double>>> sources;

    public SourceInfoService(Map<String, Map<SourceCategory, Map<String, Double>>> sources) {
        this.sources = sources;
    }

    public List<SourceEntry> entries(String skillId) {
        if (skillId == null || sources == null) {
            return List.of();
        }
        Map<SourceCategory, Map<String, Double>> skillSources = sources.get(skillId);
        if (skillSources == null) {
            return List.of();
        }
        List<SourceEntry> entries = new ArrayList<>();
        for (Map.Entry<SourceCategory, Map<String, Double>> categoryEntry : skillSources.entrySet()) {
            SourceCategory category = categoryEntry.getKey();
            if (category == null || categoryEntry.getValue() == null) {
                continue;
            }
            for (Map.Entry<String, Double> sourceEntry : categoryEntry.getValue().entrySet()) {
                String key = sourceEntry.getKey();
                Double amount = sourceEntry.getValue();
                if (key == null || key.isBlank() || amount == null || !Double.isFinite(amount) || amount <= 0.0D) {
                    continue;
                }
                entries.add(new SourceEntry(
                        category,
                        key,
                        amount,
                        behaviorKey(skillId, category),
                        formulaKey(skillId, category)
                ));
            }
        }
        return List.copyOf(entries);
    }

    private String behaviorKey(String skillId, SourceCategory category) {
        return switch (skillId.toLowerCase(Locale.ROOT)) {
            case "farming" -> "source.behavior.farming";
            case "foraging" -> "source.behavior.foraging";
            case "mining" -> "source.behavior.mining";
            case "excavation" -> "source.behavior.excavation";
            case "fishing" -> "source.behavior.fishing";
            case "fighting" -> "source.behavior.fighting";
            case "archery" -> "source.behavior.archery";
            case "alchemy" -> "source.behavior.alchemy";
            case "enchanting" -> "source.behavior.enchanting";
            case "agility" -> "source.behavior.agility";
            case "defense" -> category == SourceCategory.DAMAGE
                    ? "source.behavior.defense.damage"
                    : "source.behavior.defense.entity";
            default -> "source.behavior.default";
        };
    }

    private String formulaKey(String skillId, SourceCategory category) {
        if ("enchanting".equalsIgnoreCase(skillId) && category == SourceCategory.ENCHANT) {
            return "source.formula.enchanting";
        }
        if ("agility".equalsIgnoreCase(skillId) && category == SourceCategory.MOVEMENT) {
            return "source.formula.agility";
        }
        return "source.formula.default";
    }

    public record SourceEntry(
            SourceCategory category,
            String key,
            double amount,
            String behaviorKey,
            String formulaKey
    ) {
    }
}
