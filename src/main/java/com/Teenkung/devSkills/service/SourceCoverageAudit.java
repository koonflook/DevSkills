package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.listener.BlockSourceClassifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent;

public final class SourceCoverageAudit {

    private static final Map<String, Set<SourceCategory>> ROUTES = routes();
    private static final Map<String, Map<SourceCategory, Set<String>>> REQUIRED_KEYS = requiredKeys();

    private SourceCoverageAudit() {
    }

    public static List<String> issues(Map<String, Map<SourceCategory, Map<String, Double>>> sources) {
        List<String> issues = new ArrayList<>();
        for (String skillId : sources.keySet()) {
            if (!ROUTES.containsKey(skillId)) {
                issues.add("No listener route for skill " + skillId);
            }
        }
        for (Map.Entry<String, Set<SourceCategory>> route : ROUTES.entrySet()) {
            Map<SourceCategory, Map<String, Double>> skillSources = sources.get(route.getKey());
            if (skillSources == null) {
                issues.add("Missing source configuration for skill " + route.getKey());
                continue;
            }
            for (SourceCategory category : skillSources.keySet()) {
                if (!route.getValue().contains(category)) {
                    issues.add("No listener route for " + route.getKey() + "." + category.name());
                }
            }
            for (Map.Entry<SourceCategory, Map<String, Double>> category : skillSources.entrySet()) {
                for (String key : category.getValue().keySet()) {
                    if (!reachable(route.getKey(), category.getKey(), key)) {
                        issues.add("Unreachable source key " + route.getKey() + "." + category.getKey().name() + "." + key);
                    }
                }
            }
            for (Map.Entry<SourceCategory, Set<String>> required : REQUIRED_KEYS.getOrDefault(route.getKey(), Map.of()).entrySet()) {
                Map<String, Double> configured = skillSources.get(required.getKey());
                if (configured == null) {
                    issues.add("Missing category " + route.getKey() + "." + required.getKey().name());
                    continue;
                }
                for (String key : required.getValue()) {
                    if (!configured.containsKey(key)) {
                        issues.add("Missing fallback " + route.getKey() + "." + required.getKey().name() + "." + key);
                    }
                }
            }
        }
        return List.copyOf(issues);
    }

    private static Map<String, Set<SourceCategory>> routes() {
        Map<String, Set<SourceCategory>> routes = new LinkedHashMap<>();
        routes.put("farming", Set.of(SourceCategory.BLOCK));
        routes.put("foraging", Set.of(SourceCategory.BLOCK));
        routes.put("mining", Set.of(SourceCategory.BLOCK));
        routes.put("excavation", Set.of(SourceCategory.BLOCK));
        routes.put("fishing", Set.of(SourceCategory.FISH));
        routes.put("fighting", Set.of(SourceCategory.ENTITY));
        routes.put("archery", Set.of(SourceCategory.ENTITY));
        routes.put("defense", Set.of(SourceCategory.ENTITY, SourceCategory.DAMAGE));
        routes.put("alchemy", Set.of(SourceCategory.BREW));
        routes.put("enchanting", Set.of(SourceCategory.ENCHANT));
        routes.put("agility", Set.of(SourceCategory.MOVEMENT));
        return Map.copyOf(routes);
    }

    private static Map<String, Map<SourceCategory, Set<String>>> requiredKeys() {
        Map<String, Map<SourceCategory, Set<String>>> keys = new LinkedHashMap<>();
        keys.put("farming", Map.of(SourceCategory.BLOCK, Set.of("DEFAULT", "WHEAT", "NETHER_WART", "MELON")));
        keys.put("foraging", Map.of(SourceCategory.BLOCK, Set.of("DEFAULT", "OAK_LOG", "CRIMSON_STEM")));
        keys.put("mining", Map.of(SourceCategory.BLOCK, Set.of("DEFAULT", "STONE", "DEEPSLATE", "DIAMOND_ORE", "ANCIENT_DEBRIS", "AMETHYST_CLUSTER")));
        keys.put("excavation", Map.of(SourceCategory.BLOCK, Set.of("DEFAULT", "DIRT", "SAND", "GRAVEL", "CLAY", "SNOW_BLOCK")));
        keys.put("fishing", Map.of(SourceCategory.FISH, Set.of("FISH", "TREASURE", "JUNK", "DEFAULT", "COD")));
        keys.put("fighting", Map.of(SourceCategory.ENTITY, Set.of("DEFAULT")));
        keys.put("archery", Map.of(SourceCategory.ENTITY, Set.of("DEFAULT")));
        keys.put("defense", Map.of(
                SourceCategory.ENTITY, Set.of("DEFAULT"),
                SourceCategory.DAMAGE, Set.of("DEFAULT", "FALL")
        ));
        keys.put("alchemy", Map.of(SourceCategory.BREW, Set.of("DEFAULT")));
        keys.put("enchanting", Map.of(SourceCategory.ENCHANT, Set.of("DEFAULT")));
        keys.put("agility", Map.of(SourceCategory.MOVEMENT, Set.of("DEFAULT")));
        return Map.copyOf(keys);
    }

    private static boolean reachable(String skillId, SourceCategory category, String rawKey) {
        String key = rawKey == null ? "" : rawKey.toUpperCase(java.util.Locale.ROOT);
        if ("DEFAULT".equals(key)) {
            return true;
        }
        return switch (category) {
            case BLOCK -> Material.matchMaterial(key) != null
                    && BlockSourceClassifier.classifyName(key, true, true).filter(skillId::equals).isPresent();
            case FISH -> Set.of("FISH", "TREASURE", "JUNK").contains(key) || Material.matchMaterial(key) != null;
            case ENTITY -> enumExists(EntityType.class, key);
            case DAMAGE -> enumExists(EntityDamageEvent.DamageCause.class, key);
            case BREW, ENCHANT, MOVEMENT -> false;
        };
    }

    private static <E extends Enum<E>> boolean enumExists(Class<E> type, String key) {
        try {
            Enum.valueOf(type, key);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
