package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityAction;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityConfig;
import com.Teenkung.devSkills.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

public final class PassiveAbilityService {

    private final DevSkills plugin;
    private final Map<PassiveAbilityAction, List<PassiveAbilityConfig>> abilities = new EnumMap<>(PassiveAbilityAction.class);

    public PassiveAbilityService(DevSkills plugin) {
        this.plugin = plugin;
        for (PassiveAbilityConfig config : plugin.configManager().passiveAbilities().values()) {
            abilities.computeIfAbsent(config.action(), ignored -> new ArrayList<>()).add(config);
        }
    }

    public List<Activation> active(Player player, PassiveAbilityAction action) {
        List<PassiveAbilityConfig> configs = abilities.get(action);
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        UserProfile profile = plugin.profileService().profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return List.of();
        }
        List<Activation> active = new ArrayList<>();
        for (PassiveAbilityConfig config : configs) {
            int skillLevel = plugin.levelerService().level(config.skillId(), profile.xp(config.skillId()));
            int abilityLevel = config.abilityLevel(skillLevel);
            if (abilityLevel > 0) {
                active.add(new Activation(config, abilityLevel, config.value(skillLevel)));
            }
        }
        return List.copyOf(active);
    }

    public record Activation(PassiveAbilityConfig config, int abilityLevel, double value) {
    }
}
