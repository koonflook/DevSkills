package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.api.DevSkillAPI;
import com.Teenkung.devSkills.api.XpGainCause;
import com.Teenkung.devSkills.api.booster.XpBoosterRequest;
import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.source.SourceContext;
import com.Teenkung.devSkills.domain.trait.Trait;
import com.Teenkung.devSkills.domain.user.UserProfile;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class DevSkillApiImpl implements DevSkillAPI {

    private final DevSkills plugin;

    public DevSkillApiImpl(DevSkills plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getSkillLevel(UUID player, String skillId) {
        ProfileService profiles = plugin.profileService();
        LevelerService leveler = plugin.levelerService();
        if (profiles == null || leveler == null) {
            return 1;
        }
        return profiles.profile(player).map(profile -> leveler.level(skillId, profile.xp(skillId))).orElse(1);
    }

    @Override
    public double getSkillXp(UUID player, String skillId) {
        ProfileService profiles = plugin.profileService();
        return profiles == null ? 0.0D : profiles.profile(player).map(profile -> profile.xp(skillId)).orElse(0.0D);
    }

    @Override
    public void addSkillXp(UUID player, String skillId, double amount, XpGainCause cause) {
        whenReady(() -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                plugin.xpService().grantXp(online, skillId, amount, cause);
            }
        });
    }

    @Override
    public void setSkillLevel(UUID player, String skillId, int level) {
        whenReady(() -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                plugin.xpService().setSkillLevel(online, skillId, level);
            }
        });
    }

    @Override
    public int getTraitLevel(UUID player, String traitId) {
        ProfileService profiles = plugin.profileService();
        TraitService traits = plugin.traitService();
        UserProfile profile = profiles == null ? null : profiles.profile(player).orElse(null);
        if (profile == null || traits == null) {
            return 0;
        }
        return traits.traitLevel(profile, traitId);
    }

    @Override
    public void addManualTraitLevels(UUID player, String traitId, int amount) {
        whenReady(() -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                plugin.xpService().addManualTraitLevels(online, traitId, amount);
            }
        });
    }

    @Override
    public Collection<Skill> getSkills() {
        return plugin.configManager().skills().values();
    }

    @Override
    public Collection<Trait> getTraits() {
        return plugin.configManager().traits().values();
    }

    @Override
    public void registerCustomSource(String skillId, SourceContext matcher, double xp) {
        whenReady(() -> plugin.sourceService().registerCustomSource(skillId, matcher, xp));
    }

    @Override
    public void refreshStats(UUID player) {
        whenReady(() -> {
            Player online = Bukkit.getPlayer(player);
            UserProfile profile = plugin.profileService().profile(player).orElse(null);
            if (online != null && profile != null) {
                plugin.mythicLibStatBridge().refresh(online, plugin.traitService().effectiveTraits(profile));
            }
        });
    }

    @Override
    public CompletionStage<XpBoosterSnapshot> createXpBooster(XpBoosterRequest request) {
        return plugin.runtimeReady().thenCompose(ignored -> plugin.boosterService().create(request));
    }

    @Override
    public CompletionStage<Boolean> removeXpBooster(UUID boosterId) {
        return plugin.runtimeReady().thenCompose(ignored -> plugin.boosterService().remove(boosterId));
    }

    @Override
    public Collection<XpBoosterSnapshot> getActiveXpBoosters(UUID player) {
        XpBoosterService boosters = plugin.boosterService();
        return boosters == null ? List.of() : boosters.activeBoosters(player);
    }

    @Override
    public double getEffectiveXpBoosterMultiplier(UUID player) {
        XpBoosterService boosters = plugin.boosterService();
        return boosters == null ? 1.0D : boosters.effectiveMultiplier(player);
    }

    @Override
    public void suppressActionBar(UUID player, Duration duration) {
        whenReady(() -> plugin.hudService().suppressActionBar(player, duration));
    }

    private void whenReady(Runnable action) {
        plugin.runtimeReady().thenRun(() -> {
            if (!plugin.isEnabled()) {
                return;
            }
            if (Bukkit.isPrimaryThread()) {
                action.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, action);
            }
        });
    }
}
