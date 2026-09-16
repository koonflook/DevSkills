package com.Teenkung.devSkills.config;

import java.util.List;

public record CustomModelDataConfig(
        List<Float> floats,
        List<Boolean> flags,
        List<String> strings,
        List<String> colors
) {

    public CustomModelDataConfig {
        floats = List.copyOf(floats);
        flags = List.copyOf(flags);
        strings = List.copyOf(strings);
        colors = List.copyOf(colors);
    }

    public boolean empty() {
        return floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty();
    }

    public static CustomModelDataConfig emptyConfig() {
        return new CustomModelDataConfig(List.of(), List.of(), List.of(), List.of());
    }
}
