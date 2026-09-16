package com.Teenkung.devSkills.config;

import com.Teenkung.devSkills.util.MiniMessageUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageManager {

    private final Map<String, YamlConfiguration> locales = new LinkedHashMap<>();

    public MessageManager(File dataFolder) {
        this(null, dataFolder);
    }

    public MessageManager(JavaPlugin plugin, File dataFolder) {
        File messagesFolder = new File(dataFolder, "messages");
        File[] files = messagesFolder.listFiles((folder, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String locale = name.substring(0, name.length() - 4);
                YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
                if (plugin != null) {
                    YamlConfiguration defaults = bundled(plugin, locale);
                    if (defaults != null) {
                        live.setDefaults(defaults);
                    }
                }
                locales.put(locale, live);
            }
        }
        if (plugin != null && !locales.containsKey("en")) {
            YamlConfiguration bundled = bundled(plugin, "en");
            if (bundled != null) {
                locales.put("en", bundled);
            }
        }
    }

    public Component component(String locale, String key, Map<String, String> placeholders) {
        YamlConfiguration config = locales.getOrDefault(locale, locales.get("en"));
        if (config == null) {
            return Component.text(key);
        }
        Map<String, String> merged = new LinkedHashMap<>(placeholders);
        merged.put("prefix", config.getString("prefix", ""));
        return MiniMessageUtil.render(config.getString(key, key), merged);
    }

    public Component component(String locale, String key) {
        return component(locale, key, Map.of());
    }

    public String raw(String locale, String key, Map<String, String> placeholders) {
        YamlConfiguration config = locales.getOrDefault(locale, locales.get("en"));
        if (config == null) {
            return key;
        }
        return config.getString(key, key);
    }

    private static YamlConfiguration bundled(JavaPlugin plugin, String locale) {
        try (InputStream input = plugin.getResource("messages/" + locale + ".yml")) {
            if (input == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to load bundled locale " + locale + ": " + exception.getMessage());
            return null;
        }
    }
}
