package com.Teenkung.devSkills.util;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class MiniMessageUtilTest {

    @Test
    void rendersMiniMessagePlaceholdersAndDisablesItemItalics() {
        Component component = MiniMessageUtil.item("<green><skill>", Map.of("skill", "Agility"));

        Assertions.assertEquals("Agility", PlainTextComponentSerializer.plainText().serialize(component));
        Assertions.assertEquals(TextDecoration.State.FALSE, component.decoration(TextDecoration.ITALIC));
    }
}
