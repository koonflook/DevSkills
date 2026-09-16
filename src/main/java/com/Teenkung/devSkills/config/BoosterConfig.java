package com.Teenkung.devSkills.config;

public record BoosterConfig(double maxEffectiveMultiplier, int cleanupIntervalSeconds) {

    public BoosterConfig {
        if (!Double.isFinite(maxEffectiveMultiplier) || maxEffectiveMultiplier <= 1.0D) {
            maxEffectiveMultiplier = 100.0D;
        }
        cleanupIntervalSeconds = Math.max(1, cleanupIntervalSeconds);
    }
}
