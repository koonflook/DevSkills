package com.Teenkung.devSkills.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class DevSkillTraitChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String traitId;
    private final int oldLevel;
    private final int newLevel;

    public DevSkillTraitChangeEvent(Player player, String traitId, int oldLevel, int newLevel) {
        super(player);
        this.traitId = traitId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public String traitId() {
        return traitId;
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
