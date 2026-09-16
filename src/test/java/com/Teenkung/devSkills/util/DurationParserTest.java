package com.Teenkung.devSkills.util;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class DurationParserTest {

    @Test
    void parsesSupportedUnits() {
        Assertions.assertEquals(Duration.ofSeconds(5), DurationParser.parse("5s"));
        Assertions.assertEquals(Duration.ofMinutes(5), DurationParser.parse("5m"));
        Assertions.assertEquals(Duration.ofHours(5), DurationParser.parse("5h"));
        Assertions.assertEquals(Duration.ofDays(5), DurationParser.parse("5d"));
    }

    @Test
    void rejectsInvalidDurations() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("0s"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("5w"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("NaNs"));
    }
}
