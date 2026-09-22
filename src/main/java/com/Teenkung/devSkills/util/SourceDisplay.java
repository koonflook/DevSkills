package com.Teenkung.devSkills.util;

import com.Teenkung.devSkills.config.UiConfig;
import com.Teenkung.devSkills.domain.source.SourceCategory;
import java.util.Map;
import java.util.Locale;

public final class SourceDisplay {

    private SourceDisplay() {
    }

    public static String key(UiConfig uiConfig, String locale, SourceCategory category, String key) {
        String sourceKey = key == null ? "" : key;
        String lookup = "source.key." + category.name().toLowerCase(Locale.ROOT) + "." + sourceKey;
        String localized = uiConfig.text(locale, lookup, Map.of());
        return localized.equals(lookup) ? DisplayNames.humanize(sourceKey) : localized;
    }
}
