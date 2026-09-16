package com.Teenkung.devSkills.config;

import com.Teenkung.devSkills.domain.user.UserProfile;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoundManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public SoundManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void play(Player player, UserProfile profile, String id) {
        if (profile != null && !profile.settings().sounds()) {
            return;
        }
        SoundEntry entry = configManager.sounds().get(id);
        if (entry == null || !entry.enabled()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), entry.sound(), SoundCategory.MASTER, entry.volume(), entry.pitch());
        } catch (RuntimeException exception) {
            if (configManager.settings().debug()) {
                plugin.getLogger().warning("Invalid sound '" + entry.sound() + "' for " + id);
            }
        }
    }
}
