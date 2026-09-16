package com.Teenkung.devSkills.listener;

import com.Teenkung.devSkills.DevSkills;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {

    private final DevSkills plugin;

    public PlayerLifecycleListener(DevSkills plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.loadProfile(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.mythicLibStatBridge().clear(event.getPlayer());
        plugin.profileService().unload(event.getPlayer());
    }
}
