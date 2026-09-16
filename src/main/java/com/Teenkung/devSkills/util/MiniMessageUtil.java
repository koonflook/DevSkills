package com.Teenkung.devSkills.util;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MiniMessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MiniMessageUtil() {
    }

    public static Component render(String template, Map<String, String> placeholders) {
        if (template == null) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(template, resolver(placeholders));
        } catch (RuntimeException exception) {
            return Component.text(template);
        }
    }

    public static Component item(String template, Map<String, String> placeholders) {
        return render(template, placeholders)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(String template, Map<String, String> placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(render(template, placeholders));
    }

    private static TagResolver resolver(Map<String, String> placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            builder.resolver(Placeholder.parsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
        }
        return builder.build();
    }
}
