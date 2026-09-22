package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.domain.ability.ManaAbilityAction;
import com.Teenkung.devSkills.domain.ability.ManaAbilityConfig;
import com.Teenkung.devSkills.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class ManaAbilityService {

    private final DevSkills plugin;
    private final NamespacedKey lightningBladeKey;
    private final Map<ManaAbilityAction, ManaAbilityConfig> abilities = new EnumMap<>(ManaAbilityAction.class);
    private final Map<UUID, Map<ManaAbilityAction, Integer>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<ManaAbilityAction, Activation>> active = new HashMap<>();
    private final Map<UUID, Map<ManaAbilityAction, BukkitTask>> expirationTasks = new HashMap<>();

    public ManaAbilityService(DevSkills plugin) {
        this.plugin = plugin;
        this.lightningBladeKey = new NamespacedKey(plugin, "lightning_blade");
        for (ManaAbilityConfig config : plugin.configManager().manaAbilities().values()) {
            ManaAbilityConfig previous = abilities.put(config.action(), config);
            if (previous != null) {
                throw new IllegalStateException("Duplicate mana ability action: " + config.action());
            }
        }
    }

    public Optional<Activation> activate(Player player, ManaAbilityAction action) {
        ManaAbilityConfig config = abilities.get(action);
        if (config == null) {
            return Optional.empty();
        }
        UserProfile profile = plugin.profileService().profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return Optional.empty();
        }
        int skillLevel = plugin.levelerService().level(config.skillId(), profile.xp(config.skillId()));
        int abilityLevel = config.abilityLevel(skillLevel);
        if (abilityLevel <= 0) {
            return Optional.empty();
        }
        int currentTick = Bukkit.getCurrentTick();
        Integer readyAt = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(action);
        if (readyAt != null && !hasReached(currentTick, readyAt)) {
            return Optional.empty();
        }
        double manaCost = config.cost(skillLevel);
        if (!plugin.manaProvider().consume(player, manaCost)) {
            return Optional.empty();
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(ManaAbilityAction.class))
                .put(action, currentTick + (int) config.cooldownTicks(skillLevel));
        Activation activation = new Activation(
                config,
                abilityLevel,
                manaCost,
                config.value(skillLevel),
                currentTick + config.durationTicks()
        );
        if (config.durationTicks() > 0) {
            active.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(ManaAbilityAction.class))
                    .put(action, activation);
            scheduleExpiration(player, action, activation);
        }
        applyImmediateEffect(player, activation);
        return Optional.of(activation);
    }

    public Optional<Activation> active(Player player, ManaAbilityAction action) {
        Map<ManaAbilityAction, Activation> states = active.get(player.getUniqueId());
        if (states == null) {
            return Optional.empty();
        }
        Activation activation = states.get(action);
        if (activation == null) {
            return Optional.empty();
        }
        if (hasReached(Bukkit.getCurrentTick(), activation.expiresAtTick())) {
            removeActive(player, action);
            return Optional.empty();
        }
        return Optional.of(activation);
    }

    public Optional<Activation> consumeActive(Player player, ManaAbilityAction action) {
        Optional<Activation> activation = active(player, action);
        activation.ifPresent(ignored -> removeActive(player, action));
        return activation;
    }

    public void clear(Player player) {
        Map<ManaAbilityAction, Activation> states = active.get(player.getUniqueId());
        if (states != null) {
            for (ManaAbilityAction action : new ArrayList<>(states.keySet())) {
                removeActive(player, action);
            }
        }
        cooldowns.remove(player.getUniqueId());
    }

    private void scheduleExpiration(Player player, ManaAbilityAction action, Activation activation) {
        cancelExpirationTask(player.getUniqueId(), action);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> expire(player, action, activation.expiresAtTick()), activation.config().durationTicks());
        expirationTasks.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(ManaAbilityAction.class))
                .put(action, task);
    }

    private void expire(Player player, ManaAbilityAction action, int expiresAtTick) {
        Map<ManaAbilityAction, Activation> states = active.get(player.getUniqueId());
        Activation activation = states == null ? null : states.get(action);
        if (activation != null
                && activation.expiresAtTick() == expiresAtTick
                && hasReached(Bukkit.getCurrentTick(), expiresAtTick)) {
            removeActive(player, action);
        }
    }

    private void removeActive(Player player, ManaAbilityAction action) {
        Map<ManaAbilityAction, Activation> states = active.get(player.getUniqueId());
        if (states != null) {
            states.remove(action);
            if (states.isEmpty()) {
                active.remove(player.getUniqueId());
            }
        }
        cancelExpirationTask(player.getUniqueId(), action);
        if (action == ManaAbilityAction.LIGHTNING_BLADE) {
            clearLightningBlade(player);
        }
    }

    private void cancelExpirationTask(UUID playerId, ManaAbilityAction action) {
        Map<ManaAbilityAction, BukkitTask> tasks = expirationTasks.get(playerId);
        if (tasks == null) {
            return;
        }
        BukkitTask task = tasks.remove(action);
        if (task != null) {
            task.cancel();
        }
        if (tasks.isEmpty()) {
            expirationTasks.remove(playerId);
        }
    }

    private void applyImmediateEffect(Player player, Activation activation) {
        int duration = Math.max(1, activation.config().durationTicks());
        if (activation.config().action() == ManaAbilityAction.SPEED_MINE) {
            int amplifier = Math.clamp((int) Math.floor(activation.value() / 10.0D) - 1, 0, 4);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.HASTE,
                    duration,
                    amplifier,
                    false,
                    false,
                    true
            ));
        } else if (activation.config().action() == ManaAbilityAction.LIGHTNING_BLADE) {
            applyLightningBlade(player, activation.value());
        }
    }

    private void applyLightningBlade(Player player, double value) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        clearLightningBlade(player);
        if (value <= 0.0D) {
            return;
        }
        attribute.addModifier(new AttributeModifier(
                lightningBladeKey,
                value / 100.0D,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.ANY
        ));
    }

    private void clearLightningBlade(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        for (AttributeModifier modifier : new ArrayList<>(attribute.getModifiers())) {
            if (lightningBladeKey.equals(modifier.getKey())) {
                attribute.removeModifier(modifier);
            }
        }
    }

    private boolean hasReached(int currentTick, int targetTick) {
        return currentTick - targetTick >= 0;
    }

    public record Activation(
            ManaAbilityConfig config,
            int abilityLevel,
            double manaCost,
            double value,
            int expiresAtTick
    ) {
    }
}
