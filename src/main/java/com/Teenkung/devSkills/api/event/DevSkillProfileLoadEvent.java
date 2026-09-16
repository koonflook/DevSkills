package com.Teenkung.devSkills.api.event;

import com.Teenkung.devSkills.domain.user.UserProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class DevSkillProfileLoadEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UserProfile profile;

    public DevSkillProfileLoadEvent(Player player, UserProfile profile) {
        super(player);
        this.profile = profile;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public UserProfile profile() {
        return profile;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
