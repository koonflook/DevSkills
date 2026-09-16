package com.Teenkung.devSkills.domain.trait;

import com.Teenkung.devSkills.util.DisplayNames;

public record StatMapping(String stat, String displayName, double perLevel, ModifierKind kind) {

    public StatMapping(String stat, double perLevel, ModifierKind kind) {
        this(stat, DisplayNames.humanize(stat), perLevel, kind);
    }
}
