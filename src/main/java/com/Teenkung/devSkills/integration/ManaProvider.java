package com.Teenkung.devSkills.integration;

import org.bukkit.entity.Player;

public interface ManaProvider {

    double mana(Player player);

    boolean consume(Player player, double amount);

    void give(Player player, double amount);
}
