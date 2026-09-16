package com.Teenkung.devSkills.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class UiConfig {

    public enum JavaRenderer {
        AUTO,
        DIALOG,
        INVENTORY
    }

    private final JavaRenderer javaRenderer;
    private final boolean bedrockForms;
    private final int bedrockPageSize;
    private final Map<String, String> bedrockImages;
    private final Map<String, Map<String, String>> text;

    private UiConfig(JavaRenderer javaRenderer, boolean bedrockForms, int bedrockPageSize, Map<String, String> bedrockImages, Map<String, Map<String, String>> text) {
        this.javaRenderer = javaRenderer;
        this.bedrockForms = bedrockForms;
        this.bedrockPageSize = bedrockPageSize;
        this.bedrockImages = Map.copyOf(bedrockImages);
        this.text = Map.copyOf(text);
    }

    public static UiConfig load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "ui.yml");
        if (!file.isFile()) {
            plugin.saveResource("ui.yml", false);
        }
        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        try (InputStream input = plugin.getResource("ui.yml")) {
            if (input != null) {
                live.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Unable to layer bundled UI defaults: " + exception.getMessage());
        }
        return from(live);
    }

    public static UiConfig from(YamlConfiguration yaml) {
        JavaRenderer renderer;
        try {
            renderer = JavaRenderer.valueOf(yaml.getString("java.renderer", "INVENTORY").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            renderer = JavaRenderer.INVENTORY;
        }
        Map<String, Map<String, String>> locales = new LinkedHashMap<>();
        ConfigurationSection textSection = yaml.getConfigurationSection("text");
        if (textSection != null) {
            for (String locale : textSection.getKeys(false)) {
                ConfigurationSection localeSection = textSection.getConfigurationSection(locale);
                if (localeSection == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                flatten(localeSection, "", values);
                locales.put(locale.toLowerCase(Locale.ROOT), Map.copyOf(values));
            }
        }
        Map<String, String> images = new LinkedHashMap<>();
        ConfigurationSection imageSection = yaml.getConfigurationSection("bedrock.images");
        if (imageSection != null) {
            flatten(imageSection, "", images);
        }
        return new UiConfig(
                renderer,
                yaml.getBoolean("bedrock.forms-enabled", true),
                Math.max(1, yaml.getInt("bedrock.page-size", 8)),
                images,
                locales
        );
    }

    public JavaRenderer javaRenderer() {
        return javaRenderer;
    }

    public boolean dialogMenusEnabled() {
        return javaRenderer == JavaRenderer.DIALOG;
    }

    public boolean bedrockForms() {
        return bedrockForms;
    }

    public int bedrockPageSize() {
        return bedrockPageSize;
    }

    public String bedrockImage(String id) {
        return bedrockImages.getOrDefault(id, "");
    }

    public String text(String locale, String key) {
        String normalized = locale == null ? "en" : locale.toLowerCase(Locale.ROOT).replace('-', '_');
        Map<String, String> localized = text.get(normalized);
        if (localized == null && normalized.contains("_")) {
            localized = text.get(normalized.substring(0, normalized.indexOf('_')));
        }
        String value = localized == null ? null : localized.get(key);
        if (value == null) {
            value = text.getOrDefault("en", Map.of()).get(key);
        }
        return value == null ? key : value;
    }

    public String text(String locale, String key, Map<String, String> placeholders) {
        String value = text(locale, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("<" + entry.getKey() + ">", entry.getValue() == null ? "" : entry.getValue());
        }
        return value;
    }

    private static void flatten(ConfigurationSection section, String prefix, Map<String, String> values) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                flatten(child, path, values);
            } else {
                values.put(path, section.getString(key, path));
            }
        }
    }
}
