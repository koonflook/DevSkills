package com.Teenkung.devSkills.gui;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.config.ConfigManager;
import com.Teenkung.devSkills.config.CustomModelDataConfig;
import com.Teenkung.devSkills.config.IconConfig;
import com.Teenkung.devSkills.config.MenuConfig;
import com.Teenkung.devSkills.config.MenuItemConfig;
import com.Teenkung.devSkills.config.MenuTemplateConfig;
import com.Teenkung.devSkills.config.MessageManager;
import com.Teenkung.devSkills.config.SoundManager;
import com.Teenkung.devSkills.config.UiConfig;
import com.Teenkung.devSkills.domain.ability.ManaAbilityConfig;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityConfig;
import com.Teenkung.devSkills.domain.skill.Reward;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.domain.trait.StatMapping;
import com.Teenkung.devSkills.domain.trait.Trait;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.integration.CumulusFormBridge;
import com.Teenkung.devSkills.integration.FloodgateBridge;
import com.Teenkung.devSkills.integration.PaperDialogBridge;
import com.Teenkung.devSkills.integration.PlaceholderApiBridge;
import com.Teenkung.devSkills.service.LevelerService;
import com.Teenkung.devSkills.service.AbilityInfoService;
import com.Teenkung.devSkills.service.SourceInfoService;
import com.Teenkung.devSkills.service.TraitService;
import com.Teenkung.devSkills.util.DisplayNames;
import com.Teenkung.devSkills.util.MenuPages;
import com.Teenkung.devSkills.util.MiniMessageUtil;
import com.Teenkung.devSkills.util.ProgressBar;
import com.Teenkung.devSkills.util.RewardDisplay;
import com.Teenkung.devSkills.util.SourceDisplay;
import com.Teenkung.devSkills.util.TraitContributions;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public final class MenuManager implements Listener {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.#");
    private static final String INVENTORY_LOCALE = "th";

    private final DevSkills plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final LevelerService levelerService;
    private final SourceInfoService sourceInfoService;
    private final AbilityInfoService abilityInfoService;
    private final TraitService traitService;
    private final FloodgateBridge floodgateBridge;
    private final CumulusFormBridge cumulusFormBridge;
    private final UiConfig uiConfig;
    private final PlaceholderApiBridge placeholderApi;
    private final PaperDialogBridge paperDialogBridge;

    public MenuManager(DevSkills plugin, ConfigManager configManager, MessageManager messageManager, SoundManager soundManager, LevelerService levelerService, TraitService traitService, FloodgateBridge floodgateBridge, CumulusFormBridge cumulusFormBridge) {
        this(plugin, configManager, messageManager, soundManager, levelerService, traitService, floodgateBridge, cumulusFormBridge, UiConfig.load(plugin), new PlaceholderApiBridge(plugin, null), new PaperDialogBridge(plugin));
    }

    public MenuManager(DevSkills plugin, ConfigManager configManager, MessageManager messageManager, SoundManager soundManager, LevelerService levelerService, TraitService traitService, FloodgateBridge floodgateBridge, CumulusFormBridge cumulusFormBridge, UiConfig uiConfig, PlaceholderApiBridge placeholderApi, PaperDialogBridge paperDialogBridge) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
        this.levelerService = levelerService;
        this.sourceInfoService = new SourceInfoService(configManager.sources());
        this.abilityInfoService = new AbilityInfoService(configManager.manaAbilities(), configManager.passiveAbilities(), levelerService);
        this.traitService = traitService;
        this.floodgateBridge = floodgateBridge;
        this.cumulusFormBridge = cumulusFormBridge;
        this.uiConfig = uiConfig;
        this.placeholderApi = placeholderApi;
        this.paperDialogBridge = paperDialogBridge;
    }

    public void openSkills(Player player, UserProfile profile) {
        openSkillsPage(player, profile, 0);
    }

    public void openSkillsPage(Player player, UserProfile profile, int requestedPage) {
        if (tryOpenBedrockForm(player, () -> cumulusFormBridge.openSkills(player, profile, requestedPage))) {
            return;
        }
        if (tryOpenSkillsDialog(player, profile, requestedPage)) {
            return;
        }
        MenuConfig config = configManager.menus().get("skills");
        MenuTemplateConfig template = config.template("skill");
        int pageSize = Math.max(1, template == null ? 1 : template.slots().size());
        List<Skill> skills = new ArrayList<>(configManager.skills().values());
        int maxPage = MenuPages.maxPage(skills.size(), pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Map<String, String> pagePlaceholders = new LinkedHashMap<>();
        pagePlaceholders.put("page", String.valueOf(page + 1));
        pagePlaceholders.put("max_page", String.valueOf(maxPage + 1));
        pagePlaceholders.put("abilities_button", uiConfig.text(INVENTORY_LOCALE, "ability.inventory_button", Map.of()));
        pagePlaceholders.put("abilities_button_lore", uiConfig.text(INVENTORY_LOCALE, "ability.inventory_button_lore", Map.of()));
        MenuHolder holder = new MenuHolder("skills:" + page);
        Inventory inventory = create(player, holder, config, pagePlaceholders);
        if (template != null) {
            int from = Math.min(skills.size(), page * pageSize);
            List<Skill> visible = skills.subList(from, Math.min(skills.size(), from + pageSize));
            for (int index = 0; index < visible.size(); index++) {
                Skill skill = visible.get(index);
                int slot = template.slots().get(index);
                inventory.setItem(slot, skillMenuItem(player, template, skill, profile));
                holder.actions().put(slot, new MenuAction("open-skill", skill.id()));
            }
        }
        Map<String, MenuAction> overrides = Map.of(
                "previous-page", new MenuAction("open-skills-page", String.valueOf(page - 1)),
                "next-page", new MenuAction("open-skills-page", String.valueOf(page + 1))
        );
        Set<String> hidden = new HashSet<>();
        if (page == 0) {
            hidden.add("previous-page");
        }
        if (page >= maxPage) {
            hidden.add("next-page");
        }
        applyStaticItems(player, inventory, holder, config, pagePlaceholders, Map.of(), overrides, hidden);
        if (config.item("unlocked-abilities") == null) {
            inventory.setItem(43, item(player, IconConfig.of(Material.ENCHANTED_BOOK), pagePlaceholders.get("abilities_button"), List.of(pagePlaceholders.get("abilities_button_lore")), Map.of(), Map.of()));
            holder.actions().put(43, new MenuAction("open-abilities", ""));
        }
        player.openInventory(inventory);
        soundManager.play(player, profile, "gui-open");
    }

    public void openProfile(Player player, UserProfile profile) {
        openProfilePage(player, profile, 0);
    }

    public void openProfilePage(Player player, UserProfile profile, int requestedPage) {
        if (tryOpenBedrockForm(player, () -> cumulusFormBridge.openProfile(player, profile, requestedPage))) {
            return;
        }
        if (tryOpenProfileDialog(player, profile, requestedPage)) {
            return;
        }
        MenuConfig config = configManager.menus().get("profile");
        MenuTemplateConfig template = config.template("trait");
        int pageSize = Math.max(1, template == null ? 1 : template.slots().size());
        List<Trait> traits = new ArrayList<>(configManager.traits().values());
        int maxPage = MenuPages.maxPage(traits.size(), pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Map<String, String> pagePlaceholders = Map.of(
                "page", String.valueOf(page + 1),
                "max_page", String.valueOf(maxPage + 1)
        );
        MenuHolder holder = new MenuHolder("profile:" + page);
        Inventory inventory = create(player, holder, config, pagePlaceholders);
        if (template != null) {
            int from = Math.min(traits.size(), page * pageSize);
            List<Trait> visible = traits.subList(from, Math.min(traits.size(), from + pageSize));
            for (int index = 0; index < visible.size(); index++) {
                Trait trait = visible.get(index);
                int slot = template.slots().get(index);
                Map<String, List<String>> lines = Map.of("stat_lines", traitStatLines(trait));
                inventory.setItem(slot, templateItem(player, template, trait.icon(), traitPlaceholders(trait, profile), lines));
                holder.actions().put(slot, new MenuAction("open-trait", trait.id()));
            }
        }
        Map<String, MenuAction> overrides = Map.of(
                "previous-page", new MenuAction("open-profile-page", String.valueOf(page - 1)),
                "next-page", new MenuAction("open-profile-page", String.valueOf(page + 1))
        );
        Set<String> hidden = new HashSet<>();
        if (page == 0) {
            hidden.add("previous-page");
        }
        if (page >= maxPage) {
            hidden.add("next-page");
        }
        applyStaticItems(player, inventory, holder, config, pagePlaceholders, Map.of(), overrides, hidden);
        player.openInventory(inventory);
        soundManager.play(player, profile, "gui-open");
    }

    public void openSkill(Player player, UserProfile profile, String skillId) {
        openSkill(player, profile, skillId, 0);
    }

    public void openSkill(Player player, UserProfile profile, String skillId, int page) {
        if (tryOpenBedrockForm(player, () -> cumulusFormBridge.openSkill(player, profile, skillId, page))) {
            return;
        }
        Skill skill = configManager.skills().get(skillId);
        if (skill == null) {
            return;
        }
        if (tryOpenSkillDialog(player, profile, skill, page)) {
            return;
        }
        MenuConfig config = configManager.menus().get("skill_progress");
        MenuTemplateConfig road = templateOrFallback(config, "road", "reward");
        List<Integer> roadSlots = road == null ? List.of() : road.slots();
        int pageSize = Math.max(1, roadSlots.size());
        int maxPage = MenuPages.maxPage(skill.maxLevel(), pageSize);
        int boundedPage = MenuPages.clampPage(page, skill.maxLevel(), pageSize);
        Map<String, String> basePlaceholders = skillPlaceholders(skill, profile);
        basePlaceholders.put("page", String.valueOf(boundedPage + 1));
        basePlaceholders.put("max_page", String.valueOf(maxPage + 1));
        basePlaceholders.put("source_button", uiConfig.text(profile.settings().locale(), "source.button", Map.of()));
        basePlaceholders.put("source_button_lore", uiConfig.text(profile.settings().locale(), "source.button_lore", Map.of()));
        MenuHolder holder = new MenuHolder("skill:" + skillId + ":" + boundedPage);
        Inventory inventory = create(player, holder, config, basePlaceholders);
        double xp = profile.xp(skill.id());
        int currentLevel = levelerService.level(skill.id(), xp);
        Map<String, List<String>> summaryLines = Map.of("ability_summary_lines", abilitySummaryLines(skill, currentLevel));
        for (int index = 0; index < roadSlots.size(); index++) {
            int level = MenuPages.levelAt(boundedPage, index, roadSlots.size());
            if (level > skill.maxLevel()) {
                break;
            }
            String state = levelState(level, currentLevel);
            MenuTemplateConfig nodeTemplate = templateOrFallback(config, state, "road");
            if (nodeTemplate == null) {
                continue;
            }
            Map<String, String> nodePlaceholders = new LinkedHashMap<>(basePlaceholders);
            nodePlaceholders.put("level", String.valueOf(level));
            nodePlaceholders.put("status", DisplayNames.humanize(state));
            nodePlaceholders.put("level_progress", NUMBER_FORMAT.format(levelProgressPercent(skill, xp, level, currentLevel)));
            List<Reward> rewards = skill.rewards().rewardsForLevel(level);
            Map<String, List<String>> linePlaceholders = new LinkedHashMap<>();
            linePlaceholders.put("reward_lines", RewardDisplay.lines(rewards, configManager.traits()));
            linePlaceholders.put("ability_lines", abilityLines(skill.id(), level));
            inventory.setItem(roadSlots.get(index), templateItem(player, nodeTemplate, null, nodePlaceholders, linePlaceholders));
        }
        Map<String, MenuAction> overrides = new HashMap<>();
        overrides.put("previous-page", new MenuAction("open-skill-page", skill.id() + ":" + (boundedPage - 1)));
        overrides.put("next-page", new MenuAction("open-skill-page", skill.id() + ":" + (boundedPage + 1)));
        overrides.put("sources", new MenuAction("open-sources", skill.id() + ":" + boundedPage));
        overrides.put("abilities", new MenuAction("open-ability-categories", abilityCategoryTarget(skill.id(), boundedPage)));
        overrides.put("back", new MenuAction("open-skills", ""));
        Set<String> hidden = new HashSet<>();
        if (boundedPage <= 0) {
            hidden.add("previous-page");
        }
        if (boundedPage >= maxPage) {
            hidden.add("next-page");
        }
        applyStaticItems(player, inventory, holder, config, basePlaceholders, summaryLines, overrides, hidden);
        MenuItemConfig summary = config.item("summary");
        if (summary != null && summary.slot() >= 0 && summary.slot() < config.size()) {
            inventory.setItem(summary.slot(), item(player, skill.icon(), summary.name(), abilitySummaryLore(summary.lore()), basePlaceholders, summaryLines));
        }
        player.openInventory(inventory);
        soundManager.play(player, profile, "gui-open");
    }

    public void openSourceInfo(Player player, UserProfile profile, String skillId, int parentPage) {
        openSourceInfo(player, profile, skillId, parentPage, 0);
    }

    public void openAbilities(Player player, UserProfile profile) {
        openAbilities(player, profile, 0);
    }

    public void openAbilities(Player player, UserProfile profile, int requestedPage) {
        if (tryOpenBedrockForm(player, () -> cumulusFormBridge.openAbilities(player, profile, requestedPage))) {
            return;
        }
        if (tryOpenAbilitiesDialog(player, profile, requestedPage)) {
            return;
        }
        openAbilityCategories(player, profile, null, 0);
    }

    private void openAbilityCategories(Player player, UserProfile profile, String skillId, int parentPage) {
        MenuConfig config = configManager.menus().get("ability_categories");
        if (config == null) {
            return;
        }
        Skill skill = skillId == null ? null : configManager.skills().get(skillId);
        if (skillId != null && skill == null) {
            return;
        }
        List<AbilityInfoService.AbilityDisplayEntry> entries = abilityInfoService.allAbilities(profile, INVENTORY_LOCALE).stream()
                .filter(entry -> skillId == null || entry.skillId().equals(skillId))
                .toList();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("ability_scope", skill == null ? "ทุกสายสกิล" : skill.displayName());
        placeholders.put("passive_count", String.valueOf(entries.stream().filter(entry -> entry.type() == AbilityInfoService.AbilityType.PASSIVE).count()));
        placeholders.put("mana_count", String.valueOf(entries.stream().filter(entry -> entry.type() == AbilityInfoService.AbilityType.MANA).count()));
        MenuHolder holder = new MenuHolder("ability-categories:" + abilityCategoryTarget(skillId, parentPage));
        Inventory inventory = create(player, holder, config, placeholders);
        Map<String, MenuAction> overrides = new HashMap<>();
        overrides.put("passive", new MenuAction("open-ability-type", abilityListTarget(skillId, AbilityInfoService.AbilityType.PASSIVE, parentPage, 0)));
        overrides.put("mana", new MenuAction("open-ability-type", abilityListTarget(skillId, AbilityInfoService.AbilityType.MANA, parentPage, 0)));
        if (skill != null) {
            overrides.put("back", new MenuAction("open-skill-page", skill.id() + ":" + parentPage));
        }
        applyStaticItems(player, inventory, holder, config, placeholders, Map.of(), overrides, Set.of());
        player.openInventory(inventory);
        soundManager.play(player, profile, "gui-open");
    }

    private void openAbilityList(Player player, UserProfile profile, String skillId, AbilityInfoService.AbilityType type, int parentPage, int requestedPage) {
        MenuConfig config = configManager.menus().get("ability_info");
        if (config == null) {
            return;
        }
        Skill scopedSkill = skillId == null ? null : configManager.skills().get(skillId);
        if (skillId != null && scopedSkill == null) {
            return;
        }
        MenuTemplateConfig entryTemplate = templateOrFallback(config, "unlocked-passive", "entry");
        List<AbilityInfoService.AbilityDisplayEntry> entries = abilityInfoService.allAbilities(profile, INVENTORY_LOCALE).stream()
                .filter(entry -> entry.type() == type)
                .filter(entry -> skillId == null || entry.skillId().equals(skillId))
                .toList();
        int pageSize = abilityPageSize(config);
        int maxPage = MenuPages.maxPage(entries.size(), pageSize);
        int page = MenuPages.clampPage(requestedPage, entries.size(), pageSize);
        Map<String, String> basePlaceholders = new LinkedHashMap<>();
        basePlaceholders.put("page", String.valueOf(page + 1));
        basePlaceholders.put("max_page", String.valueOf(maxPage + 1));
        basePlaceholders.put("ability_type", type.displayName(true));
        basePlaceholders.put("ability_scope", scopedSkill == null ? "ทุกสายสกิล" : scopedSkill.displayName());
        MenuHolder holder = new MenuHolder("ability-list:" + abilityListTarget(skillId, type, parentPage, page));
        Inventory inventory = create(player, holder, config, basePlaceholders);
        if (entryTemplate != null) {
            int from = Math.min(entries.size(), page * pageSize);
            List<AbilityInfoService.AbilityDisplayEntry> visible = entries.subList(from, Math.min(entries.size(), from + pageSize));
            for (int index = 0; index < visible.size(); index++) {
                AbilityInfoService.AbilityDisplayEntry entry = visible.get(index);
                Skill skill = configManager.skills().get(entry.skillId());
                MenuTemplateConfig displayTemplate = abilityTemplate(config, entry);
                if (displayTemplate == null || index >= displayTemplate.slots().size()) {
                    continue;
                }
                Map<String, String> placeholders = new LinkedHashMap<>(basePlaceholders);
                placeholders.put("ability", entry.name());
                placeholders.put("ability_type", entry.type().displayName(true));
                placeholders.put("skill", skill == null ? entry.skillId() : skill.displayName());
                placeholders.put("ability_level", String.valueOf(entry.abilityLevel()));
                placeholders.put("unlock_level", String.valueOf(entry.unlockLevel()));
                placeholders.put("ability_description", entry.description());
                placeholders.put("ability_detail", entry.detail());
                placeholders.put("ability_status", uiConfig.text(INVENTORY_LOCALE, entry.unlocked() ? "ability.status_unlocked" : "ability.status_locked", Map.of()));
                placeholders.put("ability_maxed", entry.maxed() ? uiConfig.text(INVENTORY_LOCALE, "ability.maxed", Map.of()) : "");
                inventory.setItem(displayTemplate.slots().get(index), templateItem(player, displayTemplate, null, placeholders, Map.of()));
            }
        }
        Map<String, MenuAction> overrides = new HashMap<>();
        overrides.put("previous-page", new MenuAction("open-ability-type-page", abilityListTarget(skillId, type, parentPage, page - 1)));
        overrides.put("next-page", new MenuAction("open-ability-type-page", abilityListTarget(skillId, type, parentPage, page + 1)));
        overrides.put("back", new MenuAction("open-ability-categories", abilityCategoryTarget(skillId, parentPage)));
        Set<String> hidden = new HashSet<>();
        if (page <= 0) {
            hidden.add("previous-page");
        }
        if (page >= maxPage) {
            hidden.add("next-page");
        }
        if (!entries.isEmpty()) {
            hidden.add("empty");
        }
        applyStaticItems(player, inventory, holder, config, basePlaceholders, Map.of(), overrides, hidden);
        player.openInventory(inventory);
        soundManager.play(player, profile, "gui-open");
    }

    public void openSourceInfo(Player player, UserProfile profile, String skillId, int parentPage, int requestedPage) {
        if (tryOpenBedrockForm(player, () -> cumulusFormBridge.openSourceInfo(player, profile, skillId, parentPage, requestedPage))) {
            return;
        }
        Skill skill = configManager.skills().get(skillId);
        if (skill == null) {
            return;
        }
        if (tryOpenSourceDialog(player, profile, skill, parentPage, requestedPage)) {
            return;
        }
        MenuConfig config = configManager.menus().get("source_info");
        if (config == null) {
            return;
        }
        MenuTemplateConfig entryTemplate = config.template("entry");
        List<SourceInfoService.SourceEntry> entries = sourceInfoService.entries(skill.id());
        int pageSize = Math.max(1, entryTemplate == null ? 1 : entryTemplate.slots().size());
        int maxPage = MenuPages.maxPage(entries.size(), pageSize);
        int page = MenuPages.clampPage(requestedPage, entries.size(), pageSize);
        String locale = profile.settings().locale();
        Map<String, String> basePlaceholders = sourcePlaceholders(skill, profile, locale, page, maxPage);
        MenuHolder holder = new MenuHolder("source:" + skill.id() + ":" + parentPage + ":" + page);
        Inventory inventory = create(player, holder, config, basePlaceholders);
        if (entryTemplate != null) {
            int from = Math.min(entries.size(), page * pageSize);
            List<SourceInfoService.SourceEntry> visible = entries.subList(from, Math.min(entries.size(), from + pageSize));
            for (int index = 0; index < visible.size(); index++) {
                SourceInfoService.SourceEntry entry = visible.get(index);
                Map<String, String> placeholders = new LinkedHashMap<>(basePlaceholders);
                placeholders.put("category", sourceCategory(locale, entry.category()));
                placeholders.put("key", SourceDisplay.key(uiConfig, locale, entry.category(), entry.key()));
                placeholders.put("value", NUMBER_FORMAT.format(entry.amount()));
                placeholders.put("behavior", uiConfig.text(locale, entry.behaviorKey(), Map.of()));
                placeholders.put("formula", uiConfig.text(locale, entry.formulaKey(), Map.of()));
                inventory.setItem(entryTemplate.slots().get(index), templateItem(player, entryTemplate, sourceIcon(entry), placeholders, Map.of()));
            }
        }
        Map<String, MenuAction> overrides = new HashMap<>();
        overrides.put("previous-page", new MenuAction("open-sources-page", sourceTarget(skill.id(), parentPage, page - 1)));
        overrides.put("next-page", new MenuAction("open-sources-page", sourceTarget(skill.id(), parentPage, page + 1)));
        overrides.put("back", new MenuAction("open-skill-page", skill.id() + ":" + parentPage));
        Set<String> hidden = new HashSet<>();
        if (page <= 0) {
            hidden.add("previous-page");
        }
        if (page >= maxPage) {
            hidden.add("next-page");
        }
        if (!entries.isEmpty()) {
            hidden.add("empty");
        }
        applyStaticItems(player, inventory, holder, config, basePlaceholders, Map.of(), overrides, hidden);
        player.openInventory(inventory);
        soundManager.play(player, profile, "gui-open");
    }

    public void openTrait(Player player, UserProfile profile, String traitId) {
        if (tryOpenBedrockForm(player, () -> cumulusFormBridge.openTrait(player, profile, traitId))) {
            return;
        }
        Trait trait = configManager.traits().get(traitId);
        if (trait == null) {
            return;
        }
        if (tryOpenTraitDialog(player, profile, trait)) {
            return;
        }
        MenuConfig config = configManager.menus().get("trait_info");
        Map<String, String> basePlaceholders = traitPlaceholders(trait, profile);
        MenuHolder holder = new MenuHolder("trait:" + traitId);
        Inventory inventory = create(player, holder, config, basePlaceholders);
        MenuTemplateConfig statTemplate = config.template("stat");
        if (statTemplate != null) {
            int index = 0;
            for (StatMapping mapping : trait.stats()) {
                if (index >= statTemplate.slots().size()) {
                    break;
                }
                Map<String, String> placeholders = new LinkedHashMap<>(basePlaceholders);
                placeholders.put("stat", mapping.displayName());
                placeholders.put("stat_id", mapping.stat());
                placeholders.put("value", NUMBER_FORMAT.format(mapping.perLevel()));
                placeholders.put("per_level", NUMBER_FORMAT.format(mapping.perLevel()));
                inventory.setItem(statTemplate.slots().get(index), templateItem(player, statTemplate, null, placeholders, Map.of()));
                index++;
            }
        }
        MenuTemplateConfig sourceTemplate = templateOrFallback(config, "source", "skill");
        if (sourceTemplate != null) {
            int index = 0;
            for (Skill skill : configManager.skills().values()) {
                if (index >= sourceTemplate.slots().size()) {
                    break;
                }
                if (!TraitContributions.feedsTrait(skill, trait.id())) {
                    continue;
                }
                int level = levelerService.level(skill.id(), profile.xp(skill.id()));
                int contribution = TraitContributions.fromSkill(skill, trait.id(), level);
                Map<String, String> placeholders = sourcePlaceholders(skill, trait, profile, contribution);
                int slot = sourceTemplate.slots().get(index);
                inventory.setItem(slot, templateItem(player, sourceTemplate, skill.icon(), placeholders, Map.of()));
                holder.actions().put(slot, new MenuAction("open-skill", skill.id()));
                index++;
            }
        }
        applyStaticItems(player, inventory, holder, config, basePlaceholders, Map.of(), Map.of("back", new MenuAction("open-profile", "")), Set.of());
        player.openInventory(inventory);
        soundManager.play(player, profile, "gui-open");
    }

    private boolean tryOpenBedrockForm(Player player, BooleanSupplier opener) {
        if (cumulusFormBridge == null || !floodgateBridge.isBedrock(player.getUniqueId())) {
            return false;
        }
        try {
            return opener.getAsBoolean();
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Unable to open a Bedrock DevSkills form for " + player.getName()
                    + "; falling back to the configured Java menu: " + rootMessage(exception));
            return false;
        }
    }

    private String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UserProfile profile = plugin.profileService().profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            player.sendMessage(messageManager.component("en", "plugin.profile-loading"));
            return;
        }
        MenuAction action = holder.actions().get(event.getRawSlot());
        if (action == null) {
            return;
        }
        soundManager.play(player, profile, "gui-click");
        switch (action.action()) {
            case "open-skills" -> openSkills(player, profile);
            case "open-profile" -> openProfile(player, profile);
            case "open-skills-page" -> openOverviewPage(player, profile, action.target(), true);
            case "open-profile-page" -> openOverviewPage(player, profile, action.target(), false);
            case "open-skill" -> openSkill(player, profile, action.target());
            case "open-skill-page" -> openSkillPage(player, profile, action.target());
            case "open-sources" -> openSourceTarget(player, profile, action.target());
            case "open-sources-page" -> openSourcePageTarget(player, profile, action.target());
            case "open-abilities" -> openAbilities(player, profile);
            case "open-abilities-page" -> openAbilitiesPage(player, profile, action.target());
            case "open-ability-categories" -> openAbilityCategoriesTarget(player, profile, action.target());
            case "open-ability-type" -> openAbilityTypeTarget(player, profile, action.target());
            case "open-ability-type-page" -> openAbilityTypeTarget(player, profile, action.target());
            case "open-trait" -> openTrait(player, profile, action.target());
            default -> {
            }
        }
    }

    private void openOverviewPage(Player player, UserProfile profile, String target, boolean skills) {
        try {
            int page = Integer.parseInt(target);
            if (skills) {
                openSkillsPage(player, profile, page);
            } else {
                openProfilePage(player, profile, page);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void openSkillPage(Player player, UserProfile profile, String target) {
        String[] parts = target.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        try {
            openSkill(player, profile, parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
        }
    }

    private void openAbilitiesPage(Player player, UserProfile profile, String target) {
        openAbilityCategories(player, profile, null, 0);
    }

    private void openAbilityCategoriesTarget(Player player, UserProfile profile, String target) {
        String[] parts = target.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        try {
            openAbilityCategories(player, profile, "all".equals(parts[0]) ? null : parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
        }
    }

    private void openAbilityTypeTarget(Player player, UserProfile profile, String target) {
        String[] parts = target.split(":", 4);
        if (parts.length != 4) {
            return;
        }
        try {
            AbilityInfoService.AbilityType type = AbilityInfoService.AbilityType.valueOf(parts[1].toUpperCase(Locale.ROOT));
            openAbilityList(player, profile, "all".equals(parts[0]) ? null : parts[0], type, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void openSourceTarget(Player player, UserProfile profile, String target) {
        String[] parts = target.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        try {
            openSourceInfo(player, profile, parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
        }
    }

    private void openSourcePageTarget(Player player, UserProfile profile, String target) {
        String[] parts = target.split(":", 3);
        if (parts.length != 3) {
            return;
        }
        try {
            openSourceInfo(player, profile, parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
        }
    }

    private boolean tryOpenSkillsDialog(Player player, UserProfile profile, int requestedPage) {
        if (!dialogEnabled()) {
            return false;
        }
        String locale = profile.settings().locale();
        List<Skill> skills = new ArrayList<>(configManager.skills().values());
        int pageSize = Math.max(1, uiConfig.bedrockPageSize());
        int maxPage = MenuPages.maxPage(skills.size(), pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int from = Math.min(skills.size(), page * pageSize);
        List<PaperDialogBridge.DialogButton> buttons = new ArrayList<>();
        for (Skill skill : skills.subList(from, Math.min(skills.size(), from + pageSize))) {
            Map<String, String> placeholders = skillPlaceholders(skill, profile);
            buttons.add(new PaperDialogBridge.DialogButton(
                    ui(player, locale, "skills.button", Map.of(
                            "skill", skill.displayName(),
                            "level", placeholders.get("level"),
                            "max_level", placeholders.get("max_level")
                    )),
                    Component.empty(),
                    150,
                    dialogAction(player.getUniqueId(), "open-skill", skill.id(), 0)
            ));
        }
        if (page > 0) {
            buttons.add(dialogButton(player, locale, "navigation.previous", "open-skills-page", "", page - 1));
        }
        if (page < maxPage) {
            buttons.add(dialogButton(player, locale, "navigation.next", "open-skills-page", "", page + 1));
        }
        buttons.add(dialogButton(player, locale, "ability.button", "open-abilities", "", 0));
        return paperDialogBridge.open(player, new PaperDialogBridge.DialogView(
                ui(player, locale, "skills.title", Map.of()),
                ui(player, locale, "skills.content", Map.of("page", String.valueOf(page + 1), "max_page", String.valueOf(maxPage + 1))),
                buttons,
                ui(player, locale, "navigation.close", Map.of()),
                2
        ));
    }

    private boolean tryOpenProfileDialog(Player player, UserProfile profile, int requestedPage) {
        if (!dialogEnabled()) {
            return false;
        }
        String locale = profile.settings().locale();
        List<Trait> traits = new ArrayList<>(configManager.traits().values());
        int pageSize = Math.max(1, uiConfig.bedrockPageSize());
        int maxPage = MenuPages.maxPage(traits.size(), pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int from = Math.min(traits.size(), page * pageSize);
        List<PaperDialogBridge.DialogButton> buttons = new ArrayList<>();
        for (Trait trait : traits.subList(from, Math.min(traits.size(), from + pageSize))) {
            buttons.add(new PaperDialogBridge.DialogButton(
                    ui(player, locale, "profile.button", Map.of(
                            "trait", trait.displayName(),
                            "level", String.valueOf(traitService.traitLevel(profile, trait.id()))
                    )),
                    Component.empty(),
                    150,
                    dialogAction(player.getUniqueId(), "open-trait", trait.id(), 0)
            ));
        }
        if (page > 0) {
            buttons.add(dialogButton(player, locale, "navigation.previous", "open-profile-page", "", page - 1));
        }
        if (page < maxPage) {
            buttons.add(dialogButton(player, locale, "navigation.next", "open-profile-page", "", page + 1));
        }
        return paperDialogBridge.open(player, new PaperDialogBridge.DialogView(
                ui(player, locale, "profile.title", Map.of()),
                ui(player, locale, "profile.content", Map.of("page", String.valueOf(page + 1), "max_page", String.valueOf(maxPage + 1))),
                buttons,
                ui(player, locale, "navigation.close", Map.of()),
                2
        ));
    }

    private boolean tryOpenSkillDialog(Player player, UserProfile profile, Skill skill, int requestedPage) {
        if (!dialogEnabled()) {
            return false;
        }
        int pageSize = uiConfig.bedrockPageSize();
        int maxPage = MenuPages.maxPage(skill.maxLevel(), pageSize);
        int page = MenuPages.clampPage(requestedPage, skill.maxLevel(), pageSize);
        double xp = profile.xp(skill.id());
        int currentLevel = levelerService.level(skill.id(), xp);
        List<String> rewards = new ArrayList<>();
        int firstLevel = page * pageSize + 1;
        int lastLevel = Math.min(skill.maxLevel(), firstLevel + pageSize - 1);
        for (int level = firstLevel; level <= lastLevel; level++) {
            List<String> rewardLines = new ArrayList<>(RewardDisplay.lines(skill.rewards().rewardsForLevel(level), configManager.traits()));
            if (rewardLines.size() == 1 && MiniMessageUtil.plain(rewardLines.getFirst(), Map.of()).contains("No rewards")) {
                rewardLines.clear();
            }
            for (String ability : abilityInfoService.unlockNames(skill.id(), level, profile.settings().locale())) {
                rewardLines.add(uiConfig.text(profile.settings().locale(), "skill.ability", Map.of("ability", ability)));
            }
            if (rewardLines.isEmpty()) {
                continue;
            }
            rewards.add(uiConfig.text(profile.settings().locale(), "skill.reward", Map.of(
                    "level", String.valueOf(level),
                    "rewards", String.join(", ", rewardLines.stream().map(line -> MiniMessageUtil.plain(line, Map.of())).toList())
            )));
        }
        Map<String, String> contentPlaceholders = Map.of(
                "level", String.valueOf(currentLevel),
                "max_level", String.valueOf(skill.maxLevel()),
                "xp", NUMBER_FORMAT.format(levelerService.xpIntoLevel(skill.id(), xp)),
                "xp_required", NUMBER_FORMAT.format(levelerService.xpRequiredForCurrentLevel(skill.id(), xp)),
                "rewards", String.join("\n", rewards)
        );
        List<PaperDialogBridge.DialogButton> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(dialogButton(player, profile.settings().locale(), "navigation.previous", "open-skill-page", skill.id(), page - 1));
        }
        if (page < maxPage) {
            buttons.add(dialogButton(player, profile.settings().locale(), "navigation.next", "open-skill-page", skill.id(), page + 1));
        }
        buttons.add(dialogButton(player, profile.settings().locale(), "source.button", "open-sources", skill.id(), page));
        buttons.add(dialogButton(player, profile.settings().locale(), "navigation.back", "open-skills", "", 0));
        return paperDialogBridge.open(player, new PaperDialogBridge.DialogView(
                render(player, skill.displayName(), Map.of()),
                ui(player, profile.settings().locale(), "skill.content", contentPlaceholders),
                buttons,
                ui(player, profile.settings().locale(), "navigation.close", Map.of()),
                3
        ));
    }

    private boolean tryOpenAbilitiesDialog(Player player, UserProfile profile, int requestedPage) {
        if (!dialogEnabled()) {
            return false;
        }
        String locale = profile.settings().locale();
        List<AbilityInfoService.AbilityInfo> entries = abilityInfoService.unlocked(profile, locale);
        int pageSize = Math.max(1, uiConfig.bedrockPageSize());
        int maxPage = MenuPages.maxPage(entries.size(), pageSize);
        int page = MenuPages.clampPage(requestedPage, entries.size(), pageSize);
        int from = Math.min(entries.size(), page * pageSize);
        List<String> rows = new ArrayList<>();
        for (AbilityInfoService.AbilityInfo entry : entries.subList(from, Math.min(entries.size(), from + pageSize))) {
            Skill skill = configManager.skills().get(entry.skillId());
            rows.add(uiConfig.text(locale, "ability.entry", Map.of(
                    "ability", entry.name(),
                    "type", entry.type(),
                    "skill", skill == null ? entry.skillId() : skill.displayName(),
                    "level", String.valueOf(entry.abilityLevel()),
                    "unlock_level", String.valueOf(entry.unlockLevel()),
                    "description", entry.description(),
                    "detail", entry.detail()
            )));
        }
        if (rows.isEmpty()) {
            rows.add(uiConfig.text(locale, "ability.empty", Map.of()));
        }
        List<PaperDialogBridge.DialogButton> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(dialogButton(player, locale, "navigation.previous", "open-abilities-page", "", page - 1));
        }
        if (page < maxPage) {
            buttons.add(dialogButton(player, locale, "navigation.next", "open-abilities-page", "", page + 1));
        }
        buttons.add(dialogButton(player, locale, "navigation.back", "open-skills", "", 0));
        return paperDialogBridge.open(player, new PaperDialogBridge.DialogView(
                ui(player, locale, "ability.title", Map.of("page", String.valueOf(page + 1), "max_page", String.valueOf(maxPage + 1))),
                ui(player, locale, "ability.content", Map.of("entries", String.join("\n\n", rows))),
                buttons,
                ui(player, locale, "navigation.close", Map.of()),
                3
        ));
    }

    private boolean tryOpenSourceDialog(Player player, UserProfile profile, Skill skill, int parentPage, int requestedPage) {
        if (!dialogEnabled()) {
            return false;
        }
        String locale = profile.settings().locale();
        List<SourceInfoService.SourceEntry> entries = sourceInfoService.entries(skill.id());
        int pageSize = Math.max(1, uiConfig.bedrockPageSize());
        int maxPage = MenuPages.maxPage(entries.size(), pageSize);
        int page = MenuPages.clampPage(requestedPage, entries.size(), pageSize);
        int from = Math.min(entries.size(), page * pageSize);
        List<String> rows = new ArrayList<>();
        for (SourceInfoService.SourceEntry entry : entries.subList(from, Math.min(entries.size(), from + pageSize))) {
            rows.add(uiConfig.text(locale, "source.entry", sourceEntryPlaceholders(locale, entry)));
        }
        if (rows.isEmpty()) {
            rows.add(uiConfig.text(locale, "source.no_sources", Map.of()));
        }
        Map<String, String> contentPlaceholders = Map.of(
                "skill", skill.displayName(),
                "page", String.valueOf(page + 1),
                "max_page", String.valueOf(maxPage + 1),
                "rows", String.join("\n", rows),
                "multiplier", uiConfig.text(locale, "source.multiplier", Map.of())
        );
        List<PaperDialogBridge.DialogButton> buttons = new ArrayList<>();
        if (page > 0) {
            buttons.add(dialogButton(player, locale, "navigation.previous", "open-sources-page", skill.id() + ":" + parentPage, page - 1));
        }
        if (page < maxPage) {
            buttons.add(dialogButton(player, locale, "navigation.next", "open-sources-page", skill.id() + ":" + parentPage, page + 1));
        }
        buttons.add(dialogButton(player, locale, "navigation.back", "open-skill-page", skill.id(), parentPage));
        return paperDialogBridge.open(player, new PaperDialogBridge.DialogView(
                ui(player, locale, "source.title", Map.of(
                        "skill", skill.displayName(),
                        "page", String.valueOf(page + 1),
                        "max_page", String.valueOf(maxPage + 1)
                )),
                ui(player, locale, "source.content", contentPlaceholders),
                buttons,
                ui(player, locale, "navigation.close", Map.of()),
                3
        ));
    }

    private boolean tryOpenTraitDialog(Player player, UserProfile profile, Trait trait) {
        if (!dialogEnabled()) {
            return false;
        }
        String locale = profile.settings().locale();
        List<String> stats = trait.stats().stream()
                .map(mapping -> uiConfig.text(locale, "trait.stat", Map.of("stat", mapping.displayName(), "value", NUMBER_FORMAT.format(mapping.perLevel()))))
                .toList();
        List<String> sources = new ArrayList<>();
        List<PaperDialogBridge.DialogButton> buttons = new ArrayList<>();
        for (Skill skill : configManager.skills().values()) {
            if (!TraitContributions.feedsTrait(skill, trait.id())) {
                continue;
            }
            int level = levelerService.level(skill.id(), profile.xp(skill.id()));
            int contribution = TraitContributions.fromSkill(skill, trait.id(), level);
            sources.add(uiConfig.text(locale, "trait.source", Map.of(
                    "skill", skill.displayName(),
                    "value", NUMBER_FORMAT.format(contribution),
                    "trait", trait.displayName()
            )));
            buttons.add(new PaperDialogBridge.DialogButton(
                    render(player, skill.displayName(), Map.of()),
                    Component.empty(),
                    150,
                    dialogAction(player.getUniqueId(), "open-skill", skill.id(), 0)
            ));
        }
        buttons.add(dialogButton(player, locale, "navigation.back", "open-profile", "", 0));
        return paperDialogBridge.open(player, new PaperDialogBridge.DialogView(
                render(player, trait.displayName(), Map.of()),
                ui(player, locale, "trait.content", Map.of(
                        "level", String.valueOf(traitService.traitLevel(profile, trait.id())),
                        "stats", String.join("\n", stats),
                        "sources", String.join("\n", sources)
                )),
                buttons,
                ui(player, locale, "navigation.close", Map.of()),
                2
        ));
    }

    private PaperDialogBridge.DialogButton dialogButton(Player player, String locale, String textKey, String action, String target, int page) {
        return new PaperDialogBridge.DialogButton(
                ui(player, locale, textKey, Map.of()),
                Component.empty(),
                100,
                dialogAction(player.getUniqueId(), action, target, page)
        );
    }

    private Runnable dialogAction(UUID playerId, String action, String target, int page) {
        return () -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player currentPlayer = Bukkit.getPlayer(playerId);
            UserProfile currentProfile = plugin.profileService().profile(playerId).orElse(null);
            MenuManager currentMenus = plugin.menuManager();
            if (currentPlayer == null || !currentPlayer.isOnline() || currentProfile == null || currentMenus == null) {
                return;
            }
            switch (action) {
                case "open-skills" -> currentMenus.openSkills(currentPlayer, currentProfile);
                case "open-profile" -> currentMenus.openProfile(currentPlayer, currentProfile);
                case "open-skills-page" -> currentMenus.openSkillsPage(currentPlayer, currentProfile, page);
                case "open-profile-page" -> currentMenus.openProfilePage(currentPlayer, currentProfile, page);
                case "open-skill" -> currentMenus.openSkill(currentPlayer, currentProfile, target);
                case "open-skill-page" -> currentMenus.openSkill(currentPlayer, currentProfile, target, page);
                case "open-sources" -> currentMenus.openSourceInfo(currentPlayer, currentProfile, target, page);
                case "open-sources-page" -> openSourceDialogTarget(currentMenus, currentPlayer, currentProfile, target, page);
                case "open-abilities" -> currentMenus.openAbilities(currentPlayer, currentProfile);
                case "open-abilities-page" -> currentMenus.openAbilities(currentPlayer, currentProfile, page);
                case "open-trait" -> currentMenus.openTrait(currentPlayer, currentProfile, target);
                default -> {
                }
            }
        });
    }

    private void openSourceDialogTarget(MenuManager menus, Player player, UserProfile profile, String target, int page) {
        String[] parts = target.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        try {
            menus.openSourceInfo(player, profile, parts[0], Integer.parseInt(parts[1]), page);
        } catch (NumberFormatException ignored) {
        }
    }

    private boolean dialogEnabled() {
        return uiConfig.dialogMenusEnabled();
    }

    private Component ui(Player player, String locale, String key, Map<String, String> placeholders) {
        return render(player, uiConfig.text(locale, key, placeholders), Map.of());
    }

    private Component render(Player player, String value, Map<String, String> placeholders) {
        return MiniMessageUtil.render(placeholderApi.apply(player, value), placeholders);
    }

    private Component itemComponent(Player player, String value, Map<String, String> placeholders) {
        return MiniMessageUtil.item(placeholderApi.apply(player, value), placeholders);
    }

    private Inventory create(Player player, MenuHolder holder, MenuConfig config, Map<String, String> placeholders) {
        Inventory inventory = Bukkit.createInventory(holder, config.size(), render(player, config.title(), placeholders));
        holder.inventory(inventory);
        ItemStack fill = item(player, config.fill().icon(), config.fill().name(), config.fill().lore(), placeholders, Map.of());
        for (int slot = 0; slot < config.size(); slot++) {
            inventory.setItem(slot, fill);
        }
        return inventory;
    }

    private void applyStaticItems(Player player, Inventory inventory, MenuHolder holder, MenuConfig config, Map<String, String> placeholders, Map<String, List<String>> linePlaceholders, Map<String, MenuAction> actionOverrides, Set<String> hiddenIds) {
        for (Map.Entry<String, MenuItemConfig> entry : config.items().entrySet()) {
            if (hiddenIds.contains(entry.getKey())) {
                continue;
            }
            MenuItemConfig menuItem = entry.getValue();
            if (menuItem.slot() < 0 || menuItem.slot() >= config.size()) {
                continue;
            }
            inventory.setItem(menuItem.slot(), item(player, menuItem.icon(), menuItem.name(), menuItem.lore(), placeholders, linePlaceholders));
            MenuAction override = actionOverrides.get(entry.getKey());
            String action = override == null ? menuItem.action() : override.action();
            String target = override == null ? "" : override.target();
            if (action != null && !action.isBlank() && !"none".equalsIgnoreCase(action)) {
                holder.actions().put(menuItem.slot(), new MenuAction(action, target));
            }
        }
    }

    private ItemStack templateItem(Player player, MenuTemplateConfig template, IconConfig iconOverride, Map<String, String> placeholders, Map<String, List<String>> linePlaceholders) {
        IconConfig icon = iconOverride == null ? template.icon() : iconOverride;
        return item(player, icon, template.name(), template.lore(), placeholders, linePlaceholders);
    }

    private ItemStack item(Player player, IconConfig icon, String name, List<String> lore, Map<String, String> placeholders, Map<String, List<String>> linePlaceholders) {
        ItemStack item = new ItemStack(icon.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(itemComponent(player, name, placeholders));
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                String placeholderName = singleLinePlaceholder(line, linePlaceholders);
                if (placeholderName != null) {
                    for (String expanded : linePlaceholders.getOrDefault(placeholderName, List.of())) {
                        components.add(itemComponent(player, expanded, placeholders));
                    }
                } else {
                    components.add(itemComponent(player, line, placeholders));
                }
            }
            meta.lore(components);
            applyCustomModelData(meta, icon);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String singleLinePlaceholder(String line, Map<String, List<String>> linePlaceholders) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) {
            return null;
        }
        String name = trimmed.substring(1, trimmed.length() - 1);
        return linePlaceholders.containsKey(name) ? name : null;
    }

    private void applyCustomModelData(ItemMeta meta, IconConfig icon) {
        if (icon.hasCustomModelDataComponent()) {
            CustomModelDataConfig config = icon.customModelDataComponent();
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(config.floats());
            component.setFlags(config.flags());
            component.setStrings(config.strings());
            component.setColors(colors(config.colors()));
            meta.setCustomModelDataComponent(component);
            return;
        }
        if (icon.customModelData() != null) {
            meta.setCustomModelData(icon.customModelData());
        }
    }

    private List<Color> colors(List<String> rawColors) {
        List<Color> colors = new ArrayList<>();
        for (String raw : rawColors) {
            try {
                String value = raw.startsWith("#") ? raw.substring(1) : raw;
                colors.add(Color.fromRGB(Integer.parseInt(value, 16)));
            } catch (RuntimeException ignored) {
            }
        }
        return colors;
    }

    private Map<String, String> skillPlaceholders(Skill skill, UserProfile profile) {
        double xp = profile.xp(skill.id());
        double percent = levelerService.progressPercent(skill.id(), xp);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("skill", skill.displayName());
        placeholders.put("skill_id", skill.id());
        placeholders.put("level", String.valueOf(levelerService.level(skill.id(), xp)));
        placeholders.put("max_level", String.valueOf(skill.maxLevel()));
        placeholders.put("xp", NUMBER_FORMAT.format(levelerService.xpIntoLevel(skill.id(), xp)));
        placeholders.put("xp_required", NUMBER_FORMAT.format(levelerService.xpRequiredForCurrentLevel(skill.id(), xp)));
        placeholders.put("xp_percent", NUMBER_FORMAT.format(percent));
        placeholders.put("progress_bar", ProgressBar.render(percent, 12));
        return placeholders;
    }

    private Map<String, String> traitPlaceholders(Trait trait, UserProfile profile) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("trait", trait.displayName());
        placeholders.put("trait_id", trait.id());
        placeholders.put("trait_level", String.valueOf(traitService.traitLevel(profile, trait.id())));
        return placeholders;
    }

    private Map<String, String> sourcePlaceholders(Skill skill, UserProfile profile, String locale, int page, int maxPage) {
        Map<String, String> placeholders = skillPlaceholders(skill, profile);
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("max_page", String.valueOf(maxPage + 1));
        placeholders.put("source_title", uiConfig.text(locale, "source.title", Map.of(
                "skill", skill.displayName(),
                "page", String.valueOf(page + 1),
                "max_page", String.valueOf(maxPage + 1)
        )));
        placeholders.put("source_summary", uiConfig.text(locale, "source.summary", Map.of()));
        placeholders.put("no_sources", uiConfig.text(locale, "source.no_sources", Map.of()));
        placeholders.put("previous_label", uiConfig.text(locale, "navigation.previous", Map.of()));
        placeholders.put("back_label", uiConfig.text(locale, "navigation.back", Map.of()));
        placeholders.put("next_label", uiConfig.text(locale, "navigation.next", Map.of()));
        return placeholders;
    }

    private Map<String, String> sourceEntryPlaceholders(String locale, SourceInfoService.SourceEntry entry) {
        return Map.of(
                "category", sourceCategory(locale, entry.category()),
                "key", SourceDisplay.key(uiConfig, locale, entry.category(), entry.key()),
                "value", NUMBER_FORMAT.format(entry.amount()),
                "behavior", uiConfig.text(locale, entry.behaviorKey(), Map.of()),
                "formula", uiConfig.text(locale, entry.formulaKey(), Map.of())
        );
    }

    private IconConfig sourceIcon(SourceInfoService.SourceEntry entry) {
        Material material = switch (entry.category()) {
            case BLOCK -> matchMaterial(entry.key(), Material.STONE);
            case ENTITY -> matchMaterial(entry.key() + "_SPAWN_EGG", Material.EGG);
            case DAMAGE -> Material.SHIELD;
            case FISH -> fishingIcon(entry.key());
            case ENCHANT -> Material.ENCHANTED_BOOK;
            case BREW -> Material.POTION;
            case MOVEMENT -> Material.FEATHER;
        };
        return IconConfig.of(material);
    }

    private Material matchMaterial(String key, Material fallback) {
        if (key == null || key.isBlank() || key.equalsIgnoreCase("DEFAULT")) {
            return fallback;
        }
        Material material = Material.matchMaterial(key);
        return material == null || !material.isItem() ? fallback : material;
    }

    private Material fishingIcon(String key) {
        if ("TREASURE".equalsIgnoreCase(key)) {
            return Material.CHEST;
        }
        if ("JUNK".equalsIgnoreCase(key)) {
            return Material.STRING;
        }
        return matchMaterial(key, Material.COD);
    }

    private String sourceCategory(String locale, SourceCategory category) {
        return uiConfig.text(locale, "source.category." + category.name().toLowerCase(Locale.ROOT), Map.of());
    }

    private String sourceTarget(String skillId, int parentPage, int page) {
        return skillId + ":" + parentPage + ":" + page;
    }

    private Map<String, String> sourcePlaceholders(Skill skill, Trait trait, UserProfile profile, int contribution) {
        Map<String, String> placeholders = skillPlaceholders(skill, profile);
        placeholders.put("source", skill.displayName());
        placeholders.put("source_id", skill.id());
        placeholders.put("source_level", placeholders.get("level"));
        placeholders.put("source_max_level", placeholders.get("max_level"));
        placeholders.put("trait", trait.displayName());
        placeholders.put("trait_id", trait.id());
        placeholders.put("contribution", NUMBER_FORMAT.format(contribution));
        return placeholders;
    }

    private ItemStack skillMenuItem(Player player, MenuTemplateConfig template, Skill skill, UserProfile profile) {
        Map<String, List<String>> lines = Map.of(
                "ability_summary_lines",
                abilitySummaryLines(skill, levelerService.level(skill.id(), profile.xp(skill.id())))
        );
        return item(player, skill.icon(), template.name(), abilitySummaryLore(template.lore()), skillPlaceholders(skill, profile), lines);
    }

    private List<String> abilitySummaryLore(List<String> lore) {
        if (lore.stream().anyMatch(line -> "<ability_summary_lines>".equals(line.trim()))) {
            return lore;
        }
        List<String> expanded = new ArrayList<>(lore);
        if (!expanded.isEmpty()) {
            expanded.add("");
        }
        expanded.add("<ability_summary_lines>");
        return expanded;
    }

    private List<String> abilitySummaryLines(Skill skill, int skillLevel) {
        List<AbilityInfoService.AbilityDisplayEntry> entries = abilityInfoService.abilitiesForSkill(skill.id(), skillLevel, INVENTORY_LOCALE);
        List<String> lines = new ArrayList<>();
        addAbilitySummaryLines(lines, entries, AbilityInfoService.AbilityType.PASSIVE, "ability.passive_header");
        addAbilitySummaryLines(lines, entries, AbilityInfoService.AbilityType.MANA, "ability.mana_header");
        return lines;
    }

    private void addAbilitySummaryLines(List<String> lines, List<AbilityInfoService.AbilityDisplayEntry> entries, AbilityInfoService.AbilityType type, String headerKey) {
        List<AbilityInfoService.AbilityDisplayEntry> matching = entries.stream()
                .filter(entry -> entry.type() == type)
                .toList();
        if (matching.isEmpty()) {
            return;
        }
        if (!lines.isEmpty()) {
            lines.add("");
        }
        lines.add(uiConfig.text(INVENTORY_LOCALE, headerKey, Map.of()));
        for (AbilityInfoService.AbilityDisplayEntry entry : matching) {
            String key = entry.unlocked() ? "ability.summary_unlocked" : "ability.summary_locked";
            String maxed = entry.maxed() ? uiConfig.text(INVENTORY_LOCALE, "ability.maxed", Map.of()) : "";
            lines.add(uiConfig.text(INVENTORY_LOCALE, key, Map.of(
                    "ability", entry.name(),
                    "level", String.valueOf(entry.abilityLevel()),
                    "unlock_level", String.valueOf(entry.unlockLevel()),
                    "maxed", maxed
            )));
        }
    }

    private List<String> traitStatLines(Trait trait) {
        List<String> lines = new ArrayList<>();
        for (StatMapping mapping : trait.stats()) {
            lines.add("<gray>" + mapping.displayName() + ": <white>+" + NUMBER_FORMAT.format(mapping.perLevel()) + "</white>/level");
        }
        return lines;
    }

    private List<String> abilityLines(String skillId, int level) {
        List<String> lines = new ArrayList<>();
        for (ManaAbilityConfig ability : configManager.manaAbilities().values()) {
            if (!ability.skillId().equals(skillId) || ability.unlockLevel() != level) {
                continue;
            }
            String abilityName = messageManager.raw(INVENTORY_LOCALE, "mana-ability.names." + ability.id(), Map.of());
            lines.add(uiConfig.text(INVENTORY_LOCALE, "ability.level_unlock_mana", Map.of("ability", abilityName)));
        }
        for (PassiveAbilityConfig ability : configManager.passiveAbilities().values()) {
            if (!ability.skillId().equals(skillId) || ability.unlockLevel() != level) {
                continue;
            }
            lines.add(uiConfig.text(INVENTORY_LOCALE, "ability.level_unlock_passive", Map.of("ability", ability.displayName(INVENTORY_LOCALE))));
        }
        return lines;
    }

    private MenuTemplateConfig abilityTemplate(MenuConfig config, AbilityInfoService.AbilityDisplayEntry entry) {
        String type = entry.type() == AbilityInfoService.AbilityType.MANA ? "mana" : "passive";
        String state = entry.unlocked() ? "unlocked" : "locked";
        return templateOrFallback(config, state + "-" + type, "entry");
    }

    private String abilityCategoryTarget(String skillId, int parentPage) {
        return (skillId == null ? "all" : skillId) + ":" + parentPage;
    }

    private String abilityListTarget(String skillId, AbilityInfoService.AbilityType type, int parentPage, int page) {
        return (skillId == null ? "all" : skillId) + ":" + type.name().toLowerCase(Locale.ROOT) + ":" + parentPage + ":" + page;
    }

    private int abilityPageSize(MenuConfig config) {
        return List.of("entry", "locked-passive", "locked-mana", "unlocked-passive", "unlocked-mana").stream()
                .map(config::template)
                .filter(Objects::nonNull)
                .mapToInt(template -> template.slots().size())
                .filter(size -> size > 0)
                .min()
                .orElse(1);
    }

    private MenuTemplateConfig templateOrFallback(MenuConfig config, String id, String fallbackId) {
        MenuTemplateConfig template = config.template(id);
        return template == null ? config.template(fallbackId) : template;
    }

    private String levelState(int level, int currentLevel) {
        if (level < currentLevel) {
            return "completed";
        }
        if (level == currentLevel) {
            return "current";
        }
        return "locked";
    }

    private double levelProgressPercent(Skill skill, double xp, int level, int currentLevel) {
        if (level < currentLevel) {
            return 100.0D;
        }
        if (level > currentLevel) {
            return 0.0D;
        }
        return levelerService.progressPercent(skill.id(), xp);
    }

}
