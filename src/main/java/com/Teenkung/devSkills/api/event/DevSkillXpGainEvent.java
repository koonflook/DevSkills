package com.Teenkung.devSkills.api.event;

import com.Teenkung.devSkills.api.XpGainCause;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class DevSkillXpGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String skillId;
    private final XpGainCause cause;
    private double amount;
    private boolean cancelled;

    public DevSkillXpGainEvent(Player player, String skillId, double amount, XpGainCause cause) {
        super(player);
        this.skillId = skillId;
        this.amount = amount;
        this.cause = cause;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public String skillId() {
        return skillId;
    }

    public XpGainCause cause() {
        return cause;
    }

    public double amount() {
        return amount;
    }

    public void amount(double amount) {
        this.amount = amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
