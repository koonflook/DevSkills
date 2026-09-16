package com.Teenkung.devSkills.domain.trait;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EffectiveTraits {

    private final Map<String, Integer> levels;
    private final Map<ModifierKind, Map<String, Double>> statTotals;

    public EffectiveTraits(Map<String, Integer> levels, Map<ModifierKind, Map<String, Double>> statTotals) {
        this.levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
        Map<ModifierKind, Map<String, Double>> copy = new LinkedHashMap<>();
        for (Map.Entry<ModifierKind, Map<String, Double>> entry : statTotals.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        this.statTotals = Collections.unmodifiableMap(copy);
    }

    public int level(String traitId) {
        return levels.getOrDefault(traitId, 0);
    }

    public Map<String, Integer> levels() {
        return levels;
    }

    public Map<ModifierKind, Map<String, Double>> statTotals() {
        return statTotals;
    }
}
