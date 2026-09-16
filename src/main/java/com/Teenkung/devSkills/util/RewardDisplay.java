package com.Teenkung.devSkills.util;

import com.Teenkung.devSkills.domain.skill.CommandReward;
import com.Teenkung.devSkills.domain.skill.ItemReward;
import com.Teenkung.devSkills.domain.skill.MessageReward;
import com.Teenkung.devSkills.domain.skill.Reward;
import com.Teenkung.devSkills.domain.skill.TraitReward;
import com.Teenkung.devSkills.domain.trait.Trait;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RewardDisplay {

    private RewardDisplay() {
    }

    public static List<String> lines(List<Reward> rewards, Map<String, Trait> traits) {
        if (rewards.isEmpty()) {
            return List.of("<dark_gray>No rewards");
        }
        List<String> lines = new ArrayList<>();
        for (Reward reward : rewards) {
            lines.add("<green>" + label(reward, traits));
        }
        return lines;
    }

    public static String label(Reward reward, Map<String, Trait> traits) {
        if (reward instanceof TraitReward traitReward) {
            Trait trait = traits.get(traitReward.traitId());
            String name = trait == null ? DisplayNames.humanize(traitReward.traitId()) : trait.displayName();
            return traitReward.amount() + "x " + name;
        }
        if (reward instanceof ItemReward itemReward) {
            return itemReward.amount() + "x " + itemName(itemReward);
        }
        if (reward instanceof CommandReward commandReward) {
            return commandReward.amount() + "x " + commandReward.displayName();
        }
        if (reward instanceof MessageReward) {
            return "Message";
        }
        return "Reward";
    }

    public static String itemName(ItemReward itemReward) {
        if (!itemReward.name().isBlank()) {
            return itemReward.name();
        }
        return DisplayNames.humanize(itemReward.material().key().value());
    }
}
