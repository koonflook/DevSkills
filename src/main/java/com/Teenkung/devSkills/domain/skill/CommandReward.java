package com.Teenkung.devSkills.domain.skill;

public record CommandReward(String command, String displayName, int amount) implements Reward {

    public CommandReward(String command) {
        this(command, "Command Reward", 1);
    }
}
