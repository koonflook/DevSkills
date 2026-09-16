package com.Teenkung.devSkills.domain.skill;

import java.util.List;
import org.bukkit.Material;

public record ItemReward(Material material, int amount, String name, List<String> lore) implements Reward {
}
