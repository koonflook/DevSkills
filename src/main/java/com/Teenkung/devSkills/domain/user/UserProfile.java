package com.Teenkung.devSkills.domain.user;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class UserProfile {

    private final UUID uuid;
    private final Map<String, Double> skillXp;
    private final Map<String, Integer> manualTraitLevels;
    private final UserSettings settings;

    public UserProfile(UUID uuid, Map<String, Double> skillXp, Map<String, Integer> manualTraitLevels, UserSettings settings) {
        this.uuid = uuid;
        this.skillXp = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : skillXp.entrySet()) {
            Double value = entry.getValue();
            if (entry.getKey() != null && value != null && Double.isFinite(value) && value >= 0.0D) {
                this.skillXp.put(entry.getKey(), value);
            }
        }
        this.manualTraitLevels = new LinkedHashMap<>(manualTraitLevels);
        this.settings = settings;
    }

    public static UserProfile empty(UUID uuid) {
        return new UserProfile(uuid, Map.of(), Map.of(), UserSettings.defaults());
    }

    public UUID uuid() {
        return uuid;
    }

    public Map<String, Double> skillXp() {
        return skillXp;
    }

    public Map<String, Integer> manualTraitLevels() {
        return manualTraitLevels;
    }

    public UserSettings settings() {
        return settings;
    }

    public double xp(String skillId) {
        return skillXp.getOrDefault(skillId, 0.0D);
    }

    public void xp(String skillId, double xp) {
        if (!Double.isFinite(xp)) {
            throw new IllegalArgumentException("Skill XP must be finite");
        }
        skillXp.put(skillId, Math.max(0.0D, xp));
    }

    public void addXp(String skillId, double xp) {
        if (!Double.isFinite(xp)) {
            throw new IllegalArgumentException("Skill XP delta must be finite");
        }
        double updated = xp(skillId) + xp;
        if (!Double.isFinite(updated)) {
            throw new IllegalArgumentException("Stored skill XP must remain finite");
        }
        xp(skillId, updated);
    }

    public int manualTraitLevel(String traitId) {
        return manualTraitLevels.getOrDefault(traitId, 0);
    }

    public void manualTraitLevel(String traitId, int amount) {
        manualTraitLevels.put(traitId, amount);
    }

    public void addManualTraitLevel(String traitId, int amount) {
        manualTraitLevel(traitId, manualTraitLevel(traitId) + amount);
    }

    public UserProfile snapshot() {
        UserSettings copiedSettings = new UserSettings(
                settings.sounds(),
                settings.actionbar(),
                settings.bossbar(),
                settings.locale()
        );
        return new UserProfile(uuid, skillXp, manualTraitLevels, copiedSettings);
    }
}
