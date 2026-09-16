package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.domain.source.SourceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SourceService {

    private final Map<String, Map<SourceCategory, Map<String, Double>>> sources;
    private final Map<String, Map<SourceCategory, Map<String, Double>>> runtimeSources = new LinkedHashMap<>();

    public SourceService(Map<String, Map<SourceCategory, Map<String, Double>>> sources) {
        this.sources = sources;
    }

    public double getXp(String skillId, SourceContext context) {
        if (skillId == null || context == null || context.category() == null || context.key() == null) {
            return 0.0D;
        }
        double scale = context.scale();
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            return 0.0D;
        }
        for (String key : resolutionKeys(context)) {
            SourceValue configured = value(sources, skillId, context.category(), key);
            SourceValue runtime = value(runtimeSources, skillId, context.category(), key);
            if (configured.present() || runtime.present()) {
                double amount = configured.amount() + runtime.amount();
                double scaled = amount * scale;
                return Double.isFinite(scaled) && scaled > 0.0D ? scaled : 0.0D;
            }
        }
        return 0.0D;
    }

    public void registerCustomSource(String skillId, SourceContext context, double xp) {
        if (skillId == null || skillId.isBlank() || context == null || context.category() == null || context.key() == null || context.key().isBlank()) {
            throw new IllegalArgumentException("A skill, source category, and source key are required");
        }
        if (!Double.isFinite(xp) || xp < 0.0D) {
            throw new IllegalArgumentException("Source XP must be a finite, non-negative number");
        }
        runtimeSources
                .computeIfAbsent(skillId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(context.category(), ignored -> new LinkedHashMap<>())
                .put(context.key().toUpperCase(Locale.ROOT), xp);
    }

    public Map<String, Map<SourceCategory, Map<String, Double>>> sources() {
        return sources;
    }

    public List<String> auditIssues() {
        return SourceCoverageAudit.issues(sources);
    }

    private List<String> resolutionKeys(SourceContext context) {
        String exact = context.key().toUpperCase(Locale.ROOT);
        List<String> keys = new ArrayList<>();
        keys.add(exact);
        if (context.category() == SourceCategory.FISH) {
            String group = FishingSourceClassifier.group(exact);
            if (!group.equals(exact)) {
                keys.add(group);
            }
        }
        if (!keys.contains("DEFAULT")) {
            keys.add("DEFAULT");
        }
        return keys;
    }

    private SourceValue value(Map<String, Map<SourceCategory, Map<String, Double>>> sourceMap, String skillId, SourceCategory category, String key) {
        Map<SourceCategory, Map<String, Double>> skillSources = sourceMap.get(skillId);
        if (skillSources == null) {
            return SourceValue.MISSING;
        }
        Map<String, Double> categorySources = skillSources.get(category);
        if (categorySources == null || !categorySources.containsKey(key)) {
            return SourceValue.MISSING;
        }
        Double raw = categorySources.get(key);
        double amount = raw == null || !Double.isFinite(raw) || raw <= 0.0D ? 0.0D : raw;
        return new SourceValue(true, amount);
    }

    private record SourceValue(boolean present, double amount) {

        private static final SourceValue MISSING = new SourceValue(false, 0.0D);
    }
}
