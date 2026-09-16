package com.Teenkung.devSkills.util;

import org.bukkit.GameMode;

public final class AgilityMovement {

    private AgilityMovement() {
    }

    public static boolean eligible(
            boolean sprinting,
            boolean swimming,
            boolean flying,
            boolean gliding,
            boolean riptiding,
            boolean insideVehicle,
            GameMode gameMode,
            boolean ignoreCreative
    ) {
        if (flying || gliding || riptiding || insideVehicle) {
            return false;
        }
        if (gameMode == GameMode.SPECTATOR) {
            return false;
        }
        if (ignoreCreative && gameMode == GameMode.CREATIVE) {
            return false;
        }
        return sprinting || swimming;
    }
}
