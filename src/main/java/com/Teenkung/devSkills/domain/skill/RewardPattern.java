package com.Teenkung.devSkills.domain.skill;

public record RewardPattern(int interval, int offset, Reward reward) {

    public boolean appliesTo(int level) {
        if (interval <= 0 || level < offset) {
            return false;
        }
        return (level - offset) % interval == 0;
    }
}
