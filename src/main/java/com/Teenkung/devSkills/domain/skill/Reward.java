package com.Teenkung.devSkills.domain.skill;

public sealed interface Reward permits TraitReward, CommandReward, MessageReward, ItemReward {
}
