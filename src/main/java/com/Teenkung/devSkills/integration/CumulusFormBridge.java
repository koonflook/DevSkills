package com.Teenkung.devSkills.integration;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.config.ConfigManager;
import com.Teenkung.devSkills.config.UiConfig;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.trait.Trait;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.gui.MenuManager;
import com.Teenkung.devSkills.service.LevelerService;
import com.Teenkung.devSkills.service.SourceInfoService;
import com.Teenkung.devSkills.service.TraitService;
import com.Teenkung.devSkills.util.MiniMessageUtil;
import com.Teenkung.devSkills.util.RewardDisplay;
import com.Teenkung.devSkills.util.SourceDisplay;
import com.Teenkung.devSkills.util.TraitContributions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;

public final class CumulusFormBridge {

    private final JavaPlugin plugin;
    private final FloodgateBridge floodgateBridge;
    private final ConfigManager configManager;
    private final LevelerService levelerService;
    private final SourceInfoService sourceInfoService;
    private final TraitService traitService;
    private final UiConfig uiConfig;
    private final PlaceholderApiBridge placeholderApi;

    public CumulusFormBridge(JavaPlugin plugin, FloodgateBridge floodgateBridge, ConfigManager configManager, LevelerService levelerService, TraitService traitService) {
        this(plugin, floodgateBridge, configManager, levelerService, traitService, UiConfig.load(plugin), new PlaceholderApiBridge(plugin, null));
    }

    public CumulusFormBridge(JavaPlugin plugin, FloodgateBridge floodgateBridge, ConfigManager configManager, LevelerService levelerService, TraitService traitService, UiConfig uiConfig, PlaceholderApiBridge placeholderApi) {
        this.plugin = plugin;
        this.floodgateBridge = floodgateBridge;
        this.configManager = configManager;
        this.levelerService = levelerService;
        this.sourceInfoService = new SourceInfoService(configManager.sources());
        this.traitService = traitService;
        this.uiConfig = uiConfig;
        this.placeholderApi = placeholderApi;
    }

    public boolean openSkills(Player player, UserProfile profile) {
        return openSkills(player, profile, 0);
    }

    public boolean openSkills(Player player, UserProfile profile, int page) {
        if (!uiConfig.bedrockForms()) {
            return false;
        }
        List<Skill> skills = new ArrayList<>(configManager.skills().values());
        Page bounds = Page.of(page, skills.size(), uiConfig.bedrockPageSize());
        String locale = profile.settings().locale();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, locale, "skills.title", Map.of()))
                .content(text(player, locale, "skills.content", Map.of("page", String.valueOf(bounds.page() + 1), "max_page", String.valueOf(bounds.maxPage() + 1))));
        UUID playerId = player.getUniqueId();
        for (Skill skill : skills.subList(bounds.from(), bounds.to())) {
            int level = levelerService.level(skill.id(), profile.xp(skill.id()));
            String label = text(player, locale, "skills.button", Map.of(
                    "skill", skill.displayName(),
                    "level", String.valueOf(level),
                    "max_level", String.valueOf(skill.maxLevel())
            ));
            addButton(builder, label, uiConfig.bedrockImage("skills." + skill.id()), () -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openSkill(currentPlayer, currentProfile, skill.id())));
        }
        navigation(builder, playerId, locale, bounds, next -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openSkillsPage(currentPlayer, currentProfile, next)), null);
        return floodgateBridge.sendForm(playerId, builder.build());
    }

    public boolean openProfile(Player player, UserProfile profile) {
        return openProfile(player, profile, 0);
    }

    public boolean openProfile(Player player, UserProfile profile, int page) {
        if (!uiConfig.bedrockForms()) {
            return false;
        }
        List<Trait> traits = new ArrayList<>(configManager.traits().values());
        Page bounds = Page.of(page, traits.size(), uiConfig.bedrockPageSize());
        String locale = profile.settings().locale();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, locale, "profile.title", Map.of()))
                .content(text(player, locale, "profile.content", Map.of("page", String.valueOf(bounds.page() + 1), "max_page", String.valueOf(bounds.maxPage() + 1))));
        UUID playerId = player.getUniqueId();
        for (Trait trait : traits.subList(bounds.from(), bounds.to())) {
            String label = text(player, locale, "profile.button", Map.of(
                    "trait", trait.displayName(),
                    "level", String.valueOf(traitService.traitLevel(profile, trait.id()))
            ));
            addButton(builder, label, uiConfig.bedrockImage("traits." + trait.id()), () -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openTrait(currentPlayer, currentProfile, trait.id())));
        }
        navigation(builder, playerId, locale, bounds, next -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openProfilePage(currentPlayer, currentProfile, next)), null);
        return floodgateBridge.sendForm(playerId, builder.build());
    }

    public boolean openSkill(Player player, UserProfile profile, String skillId) {
        return openSkill(player, profile, skillId, 0);
    }

    public boolean openSkill(Player player, UserProfile profile, String skillId, int page) {
        Skill skill = configManager.skills().get(skillId);
        if (!uiConfig.bedrockForms() || skill == null) {
            return false;
        }
        double xp = profile.xp(skillId);
        int level = levelerService.level(skillId, xp);
        List<String> rewardLines = new ArrayList<>();
        for (int rewardLevel = Math.max(1, level); rewardLevel <= skill.maxLevel(); rewardLevel++) {
            List<String> rewards = RewardDisplay.lines(skill.rewards().rewardsForLevel(rewardLevel), configManager.traits());
            if (rewards.size() == 1 && MiniMessageUtil.plain(rewards.getFirst(), Map.of()).contains("No rewards")) {
                continue;
            }
            rewardLines.add(text(player, profile.settings().locale(), "skill.reward", Map.of(
                    "level", String.valueOf(rewardLevel),
                    "rewards", String.join(", ", rewards.stream().map(line -> MiniMessageUtil.plain(line, Map.of())).toList())
            )));
        }
        if (rewardLines.isEmpty()) {
            rewardLines.add(text(player, profile.settings().locale(), "common.no_rewards", Map.of()));
        }
        Page bounds = Page.of(page, rewardLines.size(), uiConfig.bedrockPageSize());
        String rewards = String.join("\n", rewardLines.subList(bounds.from(), bounds.to()));
        String content = text(player, profile.settings().locale(), "skill.content", Map.of(
                "level", String.valueOf(level),
                "max_level", String.valueOf(skill.maxLevel()),
                "xp", Long.toString(Math.round(levelerService.xpIntoLevel(skillId, xp))),
                "xp_required", Long.toString(Math.round(levelerService.xpRequiredForCurrentLevel(skillId, xp))),
                "rewards", rewards
        ));
        UUID playerId = player.getUniqueId();
        SimpleForm.Builder builder = SimpleForm.builder().title(render(player, skill.displayName())).content(content);
        addButton(
                builder,
                text(player, profile.settings().locale(), "source.button", Map.of()),
                null,
                () -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openSourceInfo(currentPlayer, currentProfile, skillId, page))
        );
        navigation(
                builder,
                playerId,
                profile.settings().locale(),
                bounds,
                next -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openSkill(currentPlayer, currentProfile, skillId, next)),
                () -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openSkills(currentPlayer, currentProfile))
        );
        return floodgateBridge.sendForm(playerId, builder.build());
    }

    public boolean openSourceInfo(Player player, UserProfile profile, String skillId, int parentPage) {
        return openSourceInfo(player, profile, skillId, parentPage, 0);
    }

    public boolean openSourceInfo(Player player, UserProfile profile, String skillId, int parentPage, int requestedPage) {
        Skill skill = configManager.skills().get(skillId);
        if (!uiConfig.bedrockForms() || skill == null) {
            return false;
        }
        String locale = profile.settings().locale();
        List<SourceInfoService.SourceEntry> entries = sourceInfoService.entries(skill.id());
        Page bounds = Page.of(requestedPage, entries.size(), uiConfig.bedrockPageSize());
        List<String> rows = new ArrayList<>();
        for (SourceInfoService.SourceEntry entry : entries.subList(bounds.from(), bounds.to())) {
            rows.add(text(player, locale, "source.entry", sourceEntryPlaceholders(player, locale, entry)));
        }
        if (rows.isEmpty()) {
            rows.add(text(player, locale, "source.no_sources", Map.of()));
        }
        String content = text(player, locale, "source.content", Map.of(
                "skill", skill.displayName(),
                "page", String.valueOf(bounds.page() + 1),
                "max_page", String.valueOf(bounds.maxPage() + 1),
                "rows", String.join("\n", rows),
                "multiplier", text(player, locale, "source.multiplier", Map.of())
        ));
        UUID playerId = player.getUniqueId();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(text(player, locale, "source.title", Map.of(
                        "skill", skill.displayName(),
                        "page", String.valueOf(bounds.page() + 1),
                        "max_page", String.valueOf(bounds.maxPage() + 1)
                )))
                .content(content);
        navigation(
                builder,
                playerId,
                locale,
                bounds,
                next -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openSourceInfo(currentPlayer, currentProfile, skillId, parentPage, next)),
                () -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openSkill(currentPlayer, currentProfile, skillId, parentPage))
        );
        return floodgateBridge.sendForm(playerId, builder.build());
    }

    public boolean openTrait(Player player, UserProfile profile, String traitId) {
        Trait trait = configManager.traits().get(traitId);
        if (!uiConfig.bedrockForms() || trait == null) {
            return false;
        }
        String locale = profile.settings().locale();
        List<String> stats = trait.stats().stream()
                .map(mapping -> text(player, locale, "trait.stat", Map.of("stat", mapping.displayName(), "value", String.valueOf(mapping.perLevel()))))
                .toList();
        List<String> sources = new ArrayList<>();
        for (Skill skill : configManager.skills().values()) {
            if (!TraitContributions.feedsTrait(skill, trait.id())) {
                continue;
            }
            int level = levelerService.level(skill.id(), profile.xp(skill.id()));
            int contribution = TraitContributions.fromSkill(skill, trait.id(), level);
            sources.add(text(player, locale, "trait.source", Map.of(
                    "skill", skill.displayName(),
                    "value", String.valueOf(contribution),
                    "trait", trait.displayName()
            )));
        }
        String content = text(player, locale, "trait.content", Map.of(
                "level", String.valueOf(traitService.traitLevel(profile, traitId)),
                "stats", String.join("\n", stats),
                "sources", String.join("\n", sources)
        ));
        UUID playerId = player.getUniqueId();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(render(player, trait.displayName()))
                .content(content);
        addButton(
                builder,
                text(player, locale, "navigation.back", Map.of()),
                null,
                () -> withProfile(playerId, (currentPlayer, currentProfile) -> currentMenus().openProfile(currentPlayer, currentProfile))
        );
        SimpleForm form = builder.build();
        return floodgateBridge.sendForm(playerId, form);
    }

    private void navigation(SimpleForm.Builder builder, UUID playerId, String locale, Page page, java.util.function.IntConsumer pageAction, Runnable backAction) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (page.page() > 0) {
            addButton(builder, text(player, locale, "navigation.previous", Map.of()), null, () -> pageAction.accept(page.page() - 1));
        }
        if (page.page() < page.maxPage()) {
            addButton(builder, text(player, locale, "navigation.next", Map.of()), null, () -> pageAction.accept(page.page() + 1));
        }
        if (backAction != null) {
            addButton(builder, text(player, locale, "navigation.back", Map.of()), null, backAction);
        }
    }

    private void addButton(SimpleForm.Builder builder, String label, String imageUrl, Runnable action) {
        Consumer<Object> callback = ignored -> schedule(action);
        if (imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            if (invokeButton(builder, label, FormImage.Type.URL, imageUrl, callback)) {
                return;
            }
            Object formImage = createFormImage(FormImage.Type.URL, imageUrl);
            if (formImage != null && invokeButton(builder, label, formImage, callback)) {
                return;
            }
            Object button = createButtonComponent(label, FormImage.Type.URL, imageUrl);
            if (button != null && invokeButton(builder, button, callback)) {
                return;
            }
        } else {
            if (invokeButton(builder, label, callback)) {
                return;
            }
            Object button = createButtonComponent(label, null, null);
            if (button != null && invokeButton(builder, button, callback)) {
                return;
            }
        }
        throw new IllegalStateException("No compatible Cumulus button callback overload is available");
    }

    /**
     * Floodgate can expose a different Cumulus version from the one used at compile time.
     * Resolve the callback overload against the runtime API instead of linking to one exact
     * convenience method signature.
     */
    private boolean invokeButton(SimpleForm.Builder builder, Object... arguments) {
        for (Method method : SimpleForm.Builder.class.getMethods()) {
            if (!method.getName().equals("button") || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                Object argument = arguments[index];
                if (argument == null ? parameterTypes[index].isPrimitive() : !parameterTypes[index].isAssignableFrom(argument.getClass())) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            try {
                method.invoke(builder, arguments);
                return true;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to access the runtime Cumulus button method", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof LinkageError linkageError) {
                    throw linkageError;
                }
                throw new IllegalStateException("Runtime Cumulus button method failed", cause);
            }
        }
        return false;
    }

    private Object createFormImage(FormImage.Type type, String data) {
        try {
            Method factory = FormImage.class.getMethod("of", FormImage.Type.class, String.class);
            return factory.invoke(null, type, data);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            return null;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof LinkageError linkageError) {
                throw linkageError;
            }
            throw new IllegalStateException("Unable to create the runtime Cumulus form image", cause);
        }
    }

    private Object createButtonComponent(String label, FormImage.Type type, String data) {
        try {
            Class<?> buttonComponentClass = Class.forName(
                    "org.geysermc.cumulus.component.ButtonComponent",
                    true,
                    SimpleForm.Builder.class.getClassLoader()
            );
            Method factory;
            if (type == null) {
                factory = buttonComponentClass.getMethod("of", String.class);
                return factory.invoke(null, label);
            }
            factory = buttonComponentClass.getMethod("of", String.class, FormImage.Type.class, String.class);
            return factory.invoke(null, label, type, data);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            return null;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof LinkageError linkageError) {
                throw linkageError;
            }
            throw new IllegalStateException("Unable to create the runtime Cumulus button", cause);
        }
    }

    private String text(Player player, String locale, String key, Map<String, String> placeholders) {
        return MiniMessageUtil.plain(placeholderApi.apply(player, uiConfig.text(locale, key, placeholders)), Map.of());
    }

    private Map<String, String> sourceEntryPlaceholders(Player player, String locale, SourceInfoService.SourceEntry entry) {
        return Map.of(
                "category", text(player, locale, "source.category." + entry.category().name().toLowerCase(Locale.ROOT), Map.of()),
                "key", SourceDisplay.key(uiConfig, locale, entry.category(), entry.key()),
                "value", String.valueOf(entry.amount()),
                "behavior", text(player, locale, entry.behaviorKey(), Map.of()),
                "formula", text(player, locale, entry.formulaKey(), Map.of())
        );
    }

    private String render(Player player, String value) {
        return MiniMessageUtil.plain(placeholderApi.apply(player, value), Map.of());
    }

    private void withProfile(UUID playerId, ProfileAction action) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || !(plugin instanceof DevSkills devSkills)) {
            return;
        }
        devSkills.profileService().profile(playerId).ifPresent(profile -> action.accept(player, profile));
    }

    private MenuManager currentMenus() {
        return ((DevSkills) plugin).menuManager();
    }

    private void schedule(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    @FunctionalInterface
    private interface ProfileAction {
        void accept(Player player, UserProfile profile);
    }

    private record Page(int page, int maxPage, int from, int to) {
        private static Page of(int requested, int total, int pageSize) {
            int max = Math.max(0, (Math.max(0, total) - 1) / Math.max(1, pageSize));
            int page = Math.max(0, Math.min(requested, max));
            int from = Math.min(total, page * pageSize);
            return new Page(page, max, from, Math.min(total, from + pageSize));
        }
    }
}
