package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.api.XpGainCause;
import com.Teenkung.devSkills.api.event.DevSkillLevelUpEvent;
import com.Teenkung.devSkills.api.event.DevSkillTraitChangeEvent;
import com.Teenkung.devSkills.api.event.DevSkillXpGainEvent;
import com.Teenkung.devSkills.config.ConfigManager;
import com.Teenkung.devSkills.config.MessageManager;
import com.Teenkung.devSkills.config.SoundManager;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.source.SourceContext;
import com.Teenkung.devSkills.domain.trait.EffectiveTraits;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.integration.MythicLibStatBridge;
import com.Teenkung.devSkills.util.ProgressBar;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

public final class XpService {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.#");

    private final ConfigManager configManager;
    private final ProfileService profileService;
    private final SourceService sourceService;
    private final XpBoosterService boosterService;
    private final LevelerService levelerService;
    private final TraitService traitService;
    private final RewardService rewardService;
    private final MythicLibStatBridge mythicLibStatBridge;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final HudService hudService;
    private final Map<String, Long> feedbackTimes = new LinkedHashMap<>();

    public XpService(JavaPlugin plugin, ConfigManager configManager, ProfileService profileService, SourceService sourceService, XpBoosterService boosterService, LevelerService levelerService, TraitService traitService, RewardService rewardService, MythicLibStatBridge mythicLibStatBridge, MessageManager messageManager, SoundManager soundManager, HudService hudService) {
        this.configManager = configManager;
        this.profileService = profileService;
        this.sourceService = sourceService;
        this.boosterService = boosterService;
        this.levelerService = levelerService;
        this.traitService = traitService;
        this.rewardService = rewardService;
        this.mythicLibStatBridge = mythicLibStatBridge;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
        this.hudService = hudService;
    }

    public void grantSourceXp(Player player, String skillId, SourceContext context, XpGainCause cause) {
        if ((configManager.settings().ignoreCreative() && player.getGameMode() == GameMode.CREATIVE)
                || !configManager.settings().worldAllowed(player.getWorld().getName())) {
            return;
        }
        double amount = sourceService.getXp(skillId, context);
        if (Double.isFinite(amount) && amount > 0.0D) {
            grantXp(player, skillId, amount, cause);
        }
    }

    public void grantXp(Player player, String skillId, double baseAmount, XpGainCause cause) {
        if (!Double.isFinite(baseAmount) || baseAmount <= 0.0D) {
            return;
        }
        Skill skill = configManager.skills().get(skillId);
        Optional<UserProfile> optionalProfile = profileService.profile(player.getUniqueId());
        if (skill == null || optionalProfile.isEmpty()) {
            return;
        }
        UserProfile profile = optionalProfile.get();
        EffectiveTraits beforeTraits = traitService.effectiveTraits(profile);
        int oldLevel = levelerService.level(skillId, profile.xp(skillId));
        double permissionMultiplier = permissionMultiplier(player);
        double traitMultiplier = traitService.xpMultiplier(profile);
        double boosterMultiplier = boosterService.effectiveMultiplier(player.getUniqueId());
        if (!validMultiplier(permissionMultiplier)
                || !validMultiplier(traitMultiplier)
                || !validMultiplier(boosterMultiplier)) {
            return;
        }
        double amount = baseAmount * permissionMultiplier * traitMultiplier * boosterMultiplier;
        if (!Double.isFinite(amount) || amount <= 0.0D) {
            return;
        }
        DevSkillXpGainEvent event = new DevSkillXpGainEvent(player, skillId, amount, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !Double.isFinite(event.amount()) || event.amount() <= 0.0D) {
            return;
        }
        double storedXp = profile.xp(skillId);
        double updatedXp = storedXp + event.amount();
        if (!Double.isFinite(storedXp) || storedXp < 0.0D || !Double.isFinite(updatedXp)) {
            return;
        }
        profile.xp(skillId, updatedXp);
        int newLevel = levelerService.level(skillId, profile.xp(skillId));
        boolean leveledUp = newLevel > oldLevel;
        if (leveledUp) {
            for (int level = oldLevel + 1; level <= newLevel; level++) {
                Bukkit.getPluginManager().callEvent(new DevSkillLevelUpEvent(player, skillId, level - 1, level));
                rewardService.applyRewards(player, profile, skill, level);
                sendLevelFeedback(player, profile, skill, level);
            }
            EffectiveTraits afterTraits = traitService.effectiveTraits(profile);
            fireTraitChanges(player, beforeTraits, afterTraits);
            mythicLibStatBridge.refresh(player, afterTraits);
        }
        sendXpFeedback(player, profile, skill, event.amount(), !leveledUp);
        soundManager.play(player, profile, "exp-gain");
        profileService.markDirty(profile);
    }

    public void setSkillLevel(Player player, String skillId, int level) {
        UserProfile profile = profileService.profile(player.getUniqueId()).orElse(null);
        Skill skill = configManager.skills().get(skillId);
        if (profile == null || skill == null) {
            return;
        }
        EffectiveTraits before = traitService.effectiveTraits(profile);
        int bounded = Math.max(1, Math.min(skill.maxLevel(), level));
        profile.xp(skillId, levelerService.xpForLevel(skillId, bounded));
        EffectiveTraits after = traitService.effectiveTraits(profile);
        fireTraitChanges(player, before, after);
        mythicLibStatBridge.refresh(player, after);
        profileService.markDirty(profile);
    }

    public void addManualTraitLevels(Player player, String traitId, int amount) {
        UserProfile profile = profileService.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        EffectiveTraits before = traitService.effectiveTraits(profile);
        profile.addManualTraitLevel(traitId, amount);
        EffectiveTraits after = traitService.effectiveTraits(profile);
        fireTraitChanges(player, before, after);
        mythicLibStatBridge.refresh(player, after);
        profileService.markDirty(profile);
    }

    public void setManualTraitLevels(Player player, String traitId, int amount) {
        UserProfile profile = profileService.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        EffectiveTraits before = traitService.effectiveTraits(profile);
        profile.manualTraitLevel(traitId, amount);
        EffectiveTraits after = traitService.effectiveTraits(profile);
        fireTraitChanges(player, before, after);
        mythicLibStatBridge.refresh(player, after);
        profileService.markDirty(profile);
    }

    public void reset(Player player, String target) {
        UserProfile profile = profileService.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        EffectiveTraits before = traitService.effectiveTraits(profile);
        if ("all".equalsIgnoreCase(target)) {
            profile.skillXp().clear();
            profile.manualTraitLevels().clear();
        } else {
            profile.xp(target, 0.0D);
        }
        EffectiveTraits after = traitService.effectiveTraits(profile);
        fireTraitChanges(player, before, after);
        mythicLibStatBridge.refresh(player, after);
        profileService.markDirty(profile);
    }

    private double permissionMultiplier(Player player) {
        if (!configManager.settings().multiplierPermissions()) {
            return configManager.settings().defaultMultiplier();
        }
        double highest = configManager.settings().defaultMultiplier();
        String prefix = configManager.settings().multiplierPrefix();
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue() || !info.getPermission().startsWith(prefix)) {
                continue;
            }
            String raw = info.getPermission().substring(prefix.length());
            try {
                double multiplier = Double.parseDouble(raw);
                if (Double.isFinite(multiplier) && multiplier > 0.0D) {
                    highest = Math.max(highest, multiplier);
                }
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return highest;
    }

    private void sendXpFeedback(Player player, UserProfile profile, Skill skill, double amount, boolean showXpBossBar) {
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + skill.id();
        long last = feedbackTimes.getOrDefault(key, 0L);
        if (now - last < configManager.settings().feedbackThrottleMillis()) {
            return;
        }
        feedbackTimes.put(key, now);
        double xp = profile.xp(skill.id());
        Map<String, String> placeholders = progressPlaceholders(skill, xp);
        placeholders.put("amount", NUMBER_FORMAT.format(amount));
        if (configManager.settings().expActionbar() && profile.settings().actionbar()) {
            hudService.showTransientActionBar(player, messageManager.component(profile.settings().locale(), "xp.actionbar", placeholders));
        }
        if (showXpBossBar && configManager.settings().expBossbar() && profile.settings().bossbar()) {
            Component title = messageManager.component(profile.settings().locale(), "xp.bossbar", placeholders);
            float progress = (float) Math.min(1.0D, levelerService.progressPercent(skill.id(), xp) / 100.0D);
            hudService.showTransientBossBar(player, title, progress, BossBar.Color.GREEN, feedbackBossBarTicks());
        }
    }

    private void sendLevelFeedback(Player player, UserProfile profile, Skill skill, int level) {
        Map<String, String> placeholders = Map.of(
                "skill", skill.displayName(),
                "level", String.valueOf(level),
                "max_level", String.valueOf(skill.maxLevel())
        );
        if (configManager.settings().levelChat()) {
            player.sendMessage(messageManager.component(profile.settings().locale(), "level.chat", placeholders));
        }
        if (configManager.settings().levelBossbar() && profile.settings().bossbar()) {
            hudService.showTransientBossBar(
                    player,
                    messageManager.component(profile.settings().locale(), "level.bossbar", placeholders),
                    1.0F,
                    BossBar.Color.YELLOW,
                    feedbackBossBarTicks()
            );
        }
        soundManager.play(player, profile, "level-up");
    }

    private long feedbackBossBarTicks() {
        return 20L * Math.max(1, configManager.settings().bossbarSeconds());
    }

    private boolean validMultiplier(double multiplier) {
        return Double.isFinite(multiplier) && multiplier > 0.0D;
    }

    private Map<String, String> progressPlaceholders(Skill skill, double xp) {
        double percent = levelerService.progressPercent(skill.id(), xp);
        return new LinkedHashMap<>(Map.of(
                "skill", skill.displayName(),
                "level", String.valueOf(levelerService.level(skill.id(), xp)),
                "max_level", String.valueOf(skill.maxLevel()),
                "xp", NUMBER_FORMAT.format(levelerService.xpIntoLevel(skill.id(), xp)),
                "xp_required", NUMBER_FORMAT.format(levelerService.xpRequiredForCurrentLevel(skill.id(), xp)),
                "xp_percent", NUMBER_FORMAT.format(percent),
                "progress_bar", ProgressBar.render(percent, 20)
        ));
    }

    private void fireTraitChanges(Player player, EffectiveTraits before, EffectiveTraits after) {
        for (Map.Entry<String, Integer> entry : after.levels().entrySet()) {
            int oldLevel = before.level(entry.getKey());
            int newLevel = entry.getValue();
            if (oldLevel != newLevel) {
                Bukkit.getPluginManager().callEvent(new DevSkillTraitChangeEvent(player, entry.getKey(), oldLevel, newLevel));
            }
        }
    }
}
