package com.Teenkung.devSkills.domain.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RewardTable {

    private final List<RewardPattern> patterns;
    private final Map<Integer, List<Reward>> levels;

    public RewardTable(List<RewardPattern> patterns, Map<Integer, List<Reward>> levels) {
        this.patterns = List.copyOf(patterns);
        Map<Integer, List<Reward>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Reward>> entry : levels.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.levels = Collections.unmodifiableMap(copy);
    }

    public List<Reward> rewardsForLevel(int level) {
        List<Reward> rewards = new ArrayList<>();
        for (RewardPattern pattern : patterns) {
            if (pattern.appliesTo(level)) {
                rewards.add(pattern.reward());
            }
        }
        rewards.addAll(levels.getOrDefault(level, List.of()));
        return rewards;
    }

    public List<RewardPattern> patterns() {
        return patterns;
    }

    public Map<Integer, List<Reward>> levels() {
        return levels;
    }
}
