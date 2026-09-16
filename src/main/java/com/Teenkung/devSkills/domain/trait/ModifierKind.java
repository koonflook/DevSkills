package com.Teenkung.devSkills.domain.trait;

public enum ModifierKind {
    FLAT,
    RELATIVE,
    ADDITIVE_MULTIPLIER;

    public static ModifierKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FLAT;
        }
        return ModifierKind.valueOf(raw.trim().toUpperCase());
    }
}
