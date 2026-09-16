package com.Teenkung.devSkills.util;

import java.util.Locale;

public final class DisplayNames {

    private DisplayNames() {
    }

    public static String humanize(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String normalized = id.toLowerCase(Locale.ROOT).replace('-', '_');
        String[] parts = normalized.split("_+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
