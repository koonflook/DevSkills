package com.Teenkung.devSkills.config;

import java.util.Set;

public record PluginSettings(
        boolean debug,
        int flushIntervalSeconds,
        boolean ignoreCreative,
        Set<String> enabledWorlds,
        Set<String> disabledWorlds,
        boolean multiplierPermissions,
        String multiplierPrefix,
        double defaultMultiplier,
        boolean expActionbar,
        boolean expBossbar,
        boolean levelChat,
        boolean levelBossbar,
        long feedbackThrottleMillis,
        int bossbarSeconds,
        int placedBlockCapPerChunk,
        boolean farmingNonAgeableAllowed,
        double agilityMinDistance,
        int agilityAwardSeconds,
        double agilityXp,
        boolean defenseEnvironmental
) {

    public boolean worldAllowed(String worldName) {
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(worldName)) {
            return false;
        }
        return !disabledWorlds.contains(worldName);
    }
}
