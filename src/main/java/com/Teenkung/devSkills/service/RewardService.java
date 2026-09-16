package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.config.ConfigManager;
import com.Teenkung.devSkills.config.MessageManager;
import com.Teenkung.devSkills.config.SoundManager;
import com.Teenkung.devSkills.domain.skill.CommandReward;
import com.Teenkung.devSkills.domain.skill.ItemReward;
import com.Teenkung.devSkills.domain.skill.MessageReward;
import com.Teenkung.devSkills.domain.skill.Reward;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.domain.trait.Trait;
import com.Teenkung.devSkills.util.MiniMessageUtil;
import com.Teenkung.devSkills.util.RewardDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class RewardService {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public RewardService(JavaPlugin plugin, ConfigManager configManager, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    public void applyRewards(Player player, UserProfile profile, Skill skill, int level) {
        for (Reward reward : skill.rewards().rewardsForLevel(level)) {
            if (reward instanceof CommandReward commandReward) {
                String command = commandReward.command()
                        .replace("<player>", player.getName())
                        .replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else if (reward instanceof MessageReward messageReward) {
                player.sendMessage(messageManager.component(profile.settings().locale(), messageReward.messageKey(), Map.of("skill", skill.displayName(), "level", String.valueOf(level))));
            } else if (reward instanceof ItemReward itemReward) {
                giveItem(player, itemReward);
            } else if (reward instanceof TraitReward traitReward) {
                Trait trait = configManager.traits().get(traitReward.traitId());
                String traitName = trait == null ? traitReward.traitId() : trait.displayName();
                player.sendMessage(messageManager.component(profile.settings().locale(), "reward.trait", Map.of("trait", traitName, "amount", String.valueOf(traitReward.amount()))));
            }
            soundManager.play(player, profile, "reward");
        }
    }

    public ConfigManager configManager() {
        return configManager;
    }

    private void giveItem(Player player, ItemReward reward) {
        ItemStack item = new ItemStack(reward.material(), reward.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = RewardDisplay.itemName(reward);
            if (!name.isBlank()) {
                meta.displayName(MiniMessageUtil.item(name, Map.of()));
            }
            List<Component> lore = new ArrayList<>();
            for (String line : reward.lore()) {
                lore.add(MiniMessageUtil.item(line, Map.of()));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        if (configManager.settings().debug()) {
            plugin.getLogger().fine("Gave item reward to " + player.getName());
        }
    }
}
