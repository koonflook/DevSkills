package com.Teenkung.devSkills.domain.source;

public record SourceContext(SourceCategory category, String key, double scale) {

    public static SourceContext of(SourceCategory category, String key) {
        return new SourceContext(category, key, 1.0D);
    }

    public static SourceContext scaled(SourceCategory category, String key, double scale) {
        return new SourceContext(category, key, scale);
    }
}
