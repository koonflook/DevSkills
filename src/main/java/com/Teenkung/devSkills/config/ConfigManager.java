package com.Teenkung.devSkills.config;

import com.Teenkung.devSkills.domain.skill.CommandReward;
import com.Teenkung.devSkills.domain.skill.ItemReward;
import com.Teenkung.devSkills.domain.skill.LevelCurve;
import com.Teenkung.devSkills.domain.skill.MessageReward;
import com.Teenkung.devSkills.domain.skill.Reward;
import com.Teenkung.devSkills.domain.skill.RewardPattern;
import com.Teenkung.devSkills.domain.skill.RewardTable;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.domain.trait.ModifierKind;
import com.Teenkung.devSkills.domain.trait.StatMapping;
import com.Teenkung.devSkills.domain.trait.Trait;
import com.Teenkung.devSkills.storage.MySqlStorage;
import com.Teenkung.devSkills.storage.SqliteStorage;
import com.Teenkung.devSkills.storage.StorageProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private static final List<String> DEFAULT_RESOURCES = List.of(
            "config.yml",
            "traits.yml",
            "skills.yml",
            "sources.yml",
            "boosters.yml",
            "hud.yml",
            "ui.yml",
            "sounds.yml",
            "messages/en.yml",
            "messages/th.yml",
            "menus/profile.yml",
            "menus/trait_info.yml",
            "menus/skills.yml",
            "menus/skill_progress.yml",
            "menus/source_info.yml"
    );

    private final JavaPlugin plugin;
    private StorageSettings storageSettings;
    private PluginSettings settings;
    private BoosterConfig boosterConfig;
    private Map<String, Trait> traits;
    private Map<String, Skill> skills;
    private Map<String, Map<SourceCategory, Map<String, Double>>> sources;
    private Map<String, SoundEntry> sounds;
    private Map<String, MenuConfig> menus;
    private MessageManager messageManager;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        boolean existingSources = new File(plugin.getDataFolder(), "sources.yml").exists();
        saveDefaults();
        if (existingSources) {
            saveSourceUpgradePreset();
        }
        YamlConfiguration config = load("config.yml");
        storageSettings = loadStorage(config);
        settings = loadSettings(config);
        boosterConfig = loadBoosterConfig(load("boosters.yml"));
        traits = loadTraits(load("traits.yml"));
        skills = loadSkills(load("skills.yml"));
        sources = loadSources(load("sources.yml"));
        sounds = loadSounds(load("sounds.yml"));
        menus = loadMenus();
        messageManager = new MessageManager(plugin, plugin.getDataFolder());
    }

    public StorageProvider createStorageProvider() {
        if ("mysql".equalsIgnoreCase(storageSettings.type())) {
            return new MySqlStorage(storageSettings);
        }
        return new SqliteStorage(plugin.getDataFolder(), storageSettings);
    }

    public StorageSettings storageSettings() {
        return storageSettings;
    }

    public PluginSettings settings() {
        return settings;
    }

    public BoosterConfig boosterConfig() {
        return boosterConfig;
    }

    public Map<String, Trait> traits() {
        return traits;
    }

    public Map<String, Skill> skills() {
        return skills;
    }

    public Map<String, Map<SourceCategory, Map<String, Double>>> sources() {
        return sources;
    }

    public Map<String, SoundEntry> sounds() {
        return sounds;
    }

    public Map<String, MenuConfig> menus() {
        return menus;
    }

    public MessageManager messageManager() {
        return messageManager;
    }

    private void saveDefaults() {
        for (String path : DEFAULT_RESOURCES) {
            File file = new File(plugin.getDataFolder(), path);
            if (!file.exists()) {
                plugin.saveResource(path, false);
            }
        }
    }

    private void saveSourceUpgradePreset() {
        File target = new File(plugin.getDataFolder(), "sources-upgrade-26.1.2.yml");
        if (target.exists()) {
            return;
        }
        try (InputStream input = plugin.getResource("sources.yml")) {
            if (input != null) {
                byte[] bundled = input.readAllBytes();
                File active = new File(plugin.getDataFolder(), "sources.yml");
                if (active.isFile() && Arrays.equals(Files.readAllBytes(active.toPath()), bundled)) {
                    return;
                }
                Files.write(target.toPath(), bundled);
                plugin.getLogger().info("Wrote sources-upgrade-26.1.2.yml for review; active sources.yml was not changed.");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to write the XP source upgrade preset: " + exception.getMessage());
        }
    }

    private YamlConfiguration load(String path) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path));
    }

    private StorageSettings loadStorage(YamlConfiguration config) {
        ConfigurationSection storage = config.getConfigurationSection("storage");
        ConfigurationSection mysql = config.getConfigurationSection("storage.mysql");
        if (storage == null || mysql == null) {
            throw new IllegalStateException("Missing storage config section");
        }
        return new StorageSettings(
                storage.getString("type", "sqlite"),
                storage.getString("sqlite-file", "devskills.db"),
                mysql.getString("host", "localhost"),
                mysql.getInt("port", 3306),
                mysql.getString("database", "devskills"),
                mysql.getString("username", "root"),
                mysql.getString("password", ""),
                mysql.getBoolean("use-ssl", false),
                mysql.getInt("pool-size", 10)
        );
    }

    private BoosterConfig loadBoosterConfig(YamlConfiguration config) {
        return new BoosterConfig(
                config.getDouble("max-effective-multiplier", 100.0D),
                config.getInt("cleanup-interval-seconds", 60)
        );
    }

    private PluginSettings loadSettings(YamlConfiguration config) {
        return new PluginSettings(
                config.getBoolean("settings.debug", false),
                config.getInt("settings.flush-interval-seconds", 60),
                config.getBoolean("settings.ignore-creative", true),
                new LinkedHashSet<>(config.getStringList("settings.enabled-worlds")),
                new LinkedHashSet<>(config.getStringList("settings.disabled-worlds")),
                config.getBoolean("settings.multiplier-permissions.enabled", true),
                config.getString("settings.multiplier-permissions.prefix", "devskill.multiplier."),
                positiveFinite(config.getDouble("settings.multiplier-permissions.default", 1.0D), 1.0D),
                config.getBoolean("settings.feedback.exp-actionbar", true),
                config.getBoolean("settings.feedback.exp-bossbar", true),
                config.getBoolean("settings.feedback.level-chat", true),
                config.getBoolean("settings.feedback.level-bossbar", true),
                config.getLong("settings.feedback.throttle-millis", 250L),
                config.getInt("settings.feedback.bossbar-seconds", 4),
                config.getInt("settings.anti-abuse.placed-block-cap-per-chunk", 4096),
                config.getBoolean("settings.anti-abuse.farming-non-ageable-allowed", true),
                config.getDouble("settings.anti-abuse.agility-min-distance", 8.0D),
                config.getInt("settings.anti-abuse.agility-award-seconds", 1),
                config.getDouble("settings.anti-abuse.agility-xp", 1.0D),
                config.getBoolean("settings.anti-abuse.defense-environmental", true)
        );
    }

    private Map<String, Trait> loadTraits(YamlConfiguration config) {
        Map<String, Trait> loaded = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("traits");
        if (root == null) {
            throw new IllegalStateException("traits.yml must contain traits");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<StatMapping> mappings = new ArrayList<>();
            ConfigurationSection stats = section.getConfigurationSection("stats");
            if (stats != null) {
                for (String stat : stats.getKeys(false)) {
                    ConfigurationSection statSection = stats.getConfigurationSection(stat);
                    if (statSection == null) {
                        continue;
                    }
                    double perLevel = statSection.getDouble("per-level", 0.0D);
                    ModifierKind kind = ModifierKind.parse(statSection.getString("type", "FLAT"));
                    mappings.add(new StatMapping(
                            stat.toUpperCase(Locale.ROOT),
                            statSection.getString("display-name", humanize(stat)),
                            perLevel,
                            kind
                    ));
                }
            }
            Integer maxLevel = section.isSet("max-level") ? nullableInt(section, "max-level") : null;
            loaded.put(id, new Trait(
                    id,
                    section.getString("display-name", id),
                    loadIcon(section.getConfigurationSection("icon"), Material.NETHER_STAR),
                    section.getInt("base-level", 0),
                    maxLevel,
                    section.getDouble("xp-multiplier-per-level", 0.0D),
                    mappings
            ));
        }
        return immutableOrdered(loaded);
    }

    private Map<String, Skill> loadSkills(YamlConfiguration config) {
        Map<String, Skill> loaded = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("skills");
        if (root == null) {
            throw new IllegalStateException("skills.yml must contain skills");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            int maxLevel = section.getInt("max-level", 100);
            String expression = section.getString("xp-curve", "100 * level^1.5");
            LevelCurve curve = buildCurve(id, expression, maxLevel);
            RewardTable rewardTable = loadRewardTable(section.getConfigurationSection("rewards"));
            loaded.put(id, new Skill(
                    id,
                    section.getString("display-name", id),
                    loadIcon(section.getConfigurationSection("icon"), Material.EXPERIENCE_BOTTLE),
                    maxLevel,
                    curve,
                    rewardTable
            ));
        }
        return immutableOrdered(loaded);
    }

    private LevelCurve buildCurve(String skillId, String expression, int maxLevel) {
        try {
            return new LevelCurve(expression, maxLevel);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid XP curve for " + skillId + ": " + expression + ". Falling back to default.");
            return new LevelCurve("100 * level^1.5", maxLevel);
        }
    }

    private RewardTable loadRewardTable(ConfigurationSection rewards) {
        if (rewards == null) {
            return new RewardTable(List.of(), Map.of());
        }
        List<RewardPattern> patterns = new ArrayList<>();
        for (Map<?, ?> raw : rewards.getMapList("patterns")) {
            Reward reward = parseReward(raw);
            if (reward != null) {
                int interval = intValue(raw.get("interval"), 1);
                int offset = intValue(raw.get("offset"), 0);
                patterns.add(new RewardPattern(interval, offset, reward));
            }
        }
        Map<Integer, List<Reward>> levels = new LinkedHashMap<>();
        ConfigurationSection levelSection = rewards.getConfigurationSection("levels");
        if (levelSection != null) {
            for (String levelKey : levelSection.getKeys(false)) {
                int level = Integer.parseInt(levelKey);
                List<Reward> parsed = new ArrayList<>();
                for (Map<?, ?> raw : levelSection.getMapList(levelKey)) {
                    Reward reward = parseReward(raw);
                    if (reward != null) {
                        parsed.add(reward);
                    }
                }
                levels.put(level, parsed);
            }
        }
        return new RewardTable(patterns, levels);
    }

    private Reward parseReward(Map<?, ?> raw) {
        Object trait = raw.get("trait");
        if (trait != null) {
            return new TraitReward(String.valueOf(trait), intValue(raw.get("amount"), 1));
        }
        Object command = raw.get("command");
        if (command != null) {
            return new CommandReward(
                    String.valueOf(command),
                    String.valueOf(raw.containsKey("display-name") ? raw.get("display-name") : "Command Reward"),
                    intValue(raw.get("amount"), 1)
            );
        }
        Object message = raw.get("message");
        if (message != null) {
            return new MessageReward(String.valueOf(message));
        }
        Object materialName = raw.get("material");
        if (materialName != null) {
            Material material = Material.matchMaterial(String.valueOf(materialName));
            if (material == null) {
                material = Material.STONE;
            }
            Object name = raw.containsKey("name") ? raw.get("name") : "";
            return new ItemReward(material, intValue(raw.get("amount"), 1), String.valueOf(name), stringList(raw.get("lore")));
        }
        return null;
    }

    private Map<String, Map<SourceCategory, Map<String, Double>>> loadSources(YamlConfiguration config) {
        Map<String, Map<SourceCategory, Map<String, Double>>> loaded = new LinkedHashMap<>();
        for (String skillId : config.getKeys(false)) {
            ConfigurationSection skillSection = config.getConfigurationSection(skillId);
            if (skillSection == null) {
                continue;
            }
            Map<SourceCategory, Map<String, Double>> categories = new LinkedHashMap<>();
            for (String categoryKey : skillSection.getKeys(false)) {
                SourceCategory category;
                try {
                    category = SourceCategory.valueOf(categoryKey.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ignoring unknown XP source category '" + categoryKey + "' for " + skillId + ".");
                    continue;
                }
                ConfigurationSection categorySection = skillSection.getConfigurationSection(categoryKey);
                Map<String, Double> values = new LinkedHashMap<>();
                if (categorySection != null) {
                    for (String key : categorySection.getKeys(false)) {
                        double xp = categorySection.getDouble(key, 0.0D);
                        if (!Double.isFinite(xp) || xp < 0.0D) {
                            plugin.getLogger().warning("Ignoring invalid XP source value for " + skillId + "." + categoryKey + "." + key + ".");
                            continue;
                        }
                        values.put(key.toUpperCase(Locale.ROOT), xp);
                    }
                }
                categories.put(category, immutableOrdered(values));
            }
            loaded.put(skillId, immutableOrdered(categories));
        }
        return immutableOrdered(loaded);
    }

    private Map<String, SoundEntry> loadSounds(YamlConfiguration config) {
        Map<String, SoundEntry> loaded = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("sounds");
        if (root == null) {
            return Map.of();
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            loaded.put(id, new SoundEntry(
                    section.getBoolean("enabled", true),
                    section.getString("sound", "minecraft:ui.button.click"),
                    (float) section.getDouble("volume", 1.0D),
                    (float) section.getDouble("pitch", 1.0D)
            ));
        }
        return immutableOrdered(loaded);
    }

    private Map<String, MenuConfig> loadMenus() {
        Map<String, MenuConfig> loaded = new LinkedHashMap<>();
        loaded.put("profile", loadMenu("menus/profile.yml"));
        loaded.put("trait_info", loadMenu("menus/trait_info.yml"));
        loaded.put("skills", loadMenu("menus/skills.yml"));
        loaded.put("skill_progress", loadMenu("menus/skill_progress.yml"));
        loaded.put("source_info", loadMenu("menus/source_info.yml"));
        return immutableOrdered(loaded);
    }

    private MenuConfig loadMenu(String path) {
        YamlConfiguration config = load(path);
        ConfigurationSection fillSection = config.getConfigurationSection("fill");
        return new MenuConfig(
                config.getString("title", "DevSkills"),
                config.getInt("size", 54),
                loadMenuItem(fillSection, -1, Material.BLACK_STAINED_GLASS_PANE, " ", "none"),
                loadMenuItems(config.getConfigurationSection("items")),
                loadMenuTemplates(config.getConfigurationSection("templates"))
        );
    }

    private Map<String, MenuItemConfig> loadMenuItems(ConfigurationSection root) {
        if (root == null) {
            return Map.of();
        }
        Map<String, MenuItemConfig> loaded = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                loaded.put(id, loadMenuItem(section, -1, Material.STONE, "", "none"));
            }
        }
        return immutableOrdered(loaded);
    }

    private Map<String, MenuTemplateConfig> loadMenuTemplates(ConfigurationSection root) {
        if (root == null) {
            return Map.of();
        }
        Map<String, MenuTemplateConfig> loaded = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                loaded.put(id, loadMenuTemplate(section, Material.STONE, "", "none"));
            }
        }
        return immutableOrdered(loaded);
    }

    private MenuItemConfig loadMenuItem(ConfigurationSection section, int fallbackSlot, Material fallbackMaterial, String fallbackName, String fallbackAction) {
        if (section == null) {
            return new MenuItemConfig(fallbackSlot, IconConfig.of(fallbackMaterial), fallbackName, List.of(), fallbackAction);
        }
        return new MenuItemConfig(
                section.getInt("slot", fallbackSlot),
                loadIcon(section, fallbackMaterial),
                section.getString("name", fallbackName),
                section.getStringList("lore"),
                section.getString("action", fallbackAction)
        );
    }

    private MenuTemplateConfig loadMenuTemplate(ConfigurationSection section, Material fallbackMaterial, String fallbackName, String fallbackAction) {
        return new MenuTemplateConfig(
                section.getIntegerList("slots"),
                loadIcon(section, fallbackMaterial),
                section.getString("name", fallbackName),
                section.getStringList("lore"),
                section.getString("action", fallbackAction)
        );
    }

    private IconConfig loadIcon(ConfigurationSection section, Material fallbackMaterial) {
        if (section == null) {
            return IconConfig.of(fallbackMaterial);
        }
        ConfigurationSection iconSection = section.getConfigurationSection("icon");
        ConfigurationSection source = iconSection == null ? section : iconSection;
        Material material = Material.matchMaterial(source.getString("material", fallbackMaterial.name()));
        if (material == null) {
            material = fallbackMaterial;
        }
        Integer customModelData = source.isSet("custom-model-data") ? source.getInt("custom-model-data") : null;
        return new IconConfig(material, customModelData, loadCustomModelData(source.getConfigurationSection("custom-model-data-component")));
    }

    private CustomModelDataConfig loadCustomModelData(ConfigurationSection section) {
        if (section == null) {
            return CustomModelDataConfig.emptyConfig();
        }
        return new CustomModelDataConfig(
                floatList(section.getList("floats", Collections.emptyList())),
                section.getBooleanList("flags"),
                section.getStringList("strings"),
                section.getStringList("colors")
        );
    }

    private Integer nullableInt(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String raw = String.valueOf(value);
        if ("null".equalsIgnoreCase(raw)) {
            return null;
        }
        return Integer.parseInt(raw);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private List<Float> floatList(List<?> values) {
        List<Float> floats = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Number number) {
                floats.add(number.floatValue());
            } else if (value != null) {
                floats.add(Float.parseFloat(String.valueOf(value)));
            }
        }
        return floats;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            for (Object entry : list) {
                strings.add(String.valueOf(entry));
            }
            return strings;
        }
        return List.of();
    }

    private String humanize(String id) {
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

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static double positiveFinite(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }
}
