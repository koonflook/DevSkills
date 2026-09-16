package com.Teenkung.devSkills.util;

import java.time.Duration;
import java.util.Locale;

public final class DurationParser {

    private DurationParser() {
    }

    public static Duration parse(String input) {
        if (input == null || input.length() < 2) {
            throw new IllegalArgumentException("Duration must contain a positive value and unit");
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        char unit = normalized.charAt(normalized.length() - 1);
        long value;
        try {
            value = Long.parseLong(normalized.substring(0, normalized.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid duration: " + input, exception);
        }
        if (value <= 0L) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        try {
            return switch (unit) {
                case 's' -> Duration.ofSeconds(value);
                case 'm' -> Duration.ofMinutes(value);
                case 'h' -> Duration.ofHours(value);
                case 'd' -> Duration.ofDays(value);
                default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
            };
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Duration is too large", exception);
        }
    }
}
