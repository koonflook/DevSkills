package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.domain.source.SourceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SourceServiceTest {

    @Test
    void resolvesExactThenFishingGroupThenDefault() {
        SourceService service = service(Map.of(
                "COD", 6.0D,
                "FISH", 4.0D,
                "TREASURE", 12.0D,
                "JUNK", 2.0D,
                "DEFAULT", 1.0D
        ));

        Assertions.assertEquals(6.0D, service.getXp("fishing", SourceContext.of(SourceCategory.FISH, "COD")));
        Assertions.assertEquals(4.0D, service.getXp("fishing", SourceContext.of(SourceCategory.FISH, "SALMON")));
        Assertions.assertEquals(12.0D, service.getXp("fishing", SourceContext.of(SourceCategory.FISH, "SADDLE")));
        Assertions.assertEquals(2.0D, service.getXp("fishing", SourceContext.of(SourceCategory.FISH, "BONE")));
        Assertions.assertEquals(1.0D, service.getXp("fishing", SourceContext.of(SourceCategory.FISH, "DIAMOND")));
    }

    @Test
    void explicitZeroDisablesAnExactSourceWithoutFallingBack() {
        SourceService service = service(Map.of("COD", 0.0D, "FISH", 4.0D, "DEFAULT", 1.0D));

        Assertions.assertEquals(0.0D, service.getXp("fishing", SourceContext.of(SourceCategory.FISH, "COD")));
    }

    @Test
    void runtimeExactSourcesRemainAdditive() {
        SourceService service = service(Map.of("COD", 6.0D, "DEFAULT", 1.0D));
        service.registerCustomSource("fishing", SourceContext.of(SourceCategory.FISH, "COD"), 2.0D);

        Assertions.assertEquals(8.0D, service.getXp("fishing", SourceContext.scaled(SourceCategory.FISH, "COD", 1.0D)));
    }

    @Test
    void entityAndDamageSourcesUseTheirCategoryDefaults() {
        SourceService service = new SourceService(Map.of(
                "fighting", Map.of(SourceCategory.ENTITY, Map.of("DEFAULT", 1.5D)),
                "defense", Map.of(SourceCategory.DAMAGE, Map.of("DEFAULT", 1.0D))
        ));

        Assertions.assertEquals(1.5D,
                service.getXp("fighting", SourceContext.of(SourceCategory.ENTITY, "BOGGED")));
        Assertions.assertEquals(1.0D,
                service.getXp("defense", SourceContext.of(SourceCategory.DAMAGE, "CONTACT")));
    }

    @Test
    void rejectsUnsafeRuntimeValuesAndScales() {
        SourceService service = service(Map.of("DEFAULT", 1.0D));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.registerCustomSource("fishing", SourceContext.of(SourceCategory.FISH, "COD"), Double.NaN));
        Assertions.assertEquals(0.0D,
                service.getXp("fishing", SourceContext.scaled(SourceCategory.FISH, "UNKNOWN", Double.POSITIVE_INFINITY)));
        Assertions.assertEquals(0.0D,
                service.getXp("fishing", SourceContext.scaled(SourceCategory.FISH, "UNKNOWN", -1.0D)));
    }

    private SourceService service(Map<String, Double> fishSources) {
        Map<SourceCategory, Map<String, Double>> categories = new LinkedHashMap<>();
        categories.put(SourceCategory.FISH, fishSources);
        return new SourceService(Map.of("fishing", categories));
    }
}
