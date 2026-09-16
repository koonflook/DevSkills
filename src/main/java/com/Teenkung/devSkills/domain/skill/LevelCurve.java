package com.Teenkung.devSkills.domain.skill;

import java.util.Arrays;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public final class LevelCurve {

    private final String expression;
    private final double[] thresholds;
    private final double[] cumulative;

    public LevelCurve(String expression, int maxLevel) {
        this.expression = expression;
        this.thresholds = new double[maxLevel + 1];
        this.cumulative = new double[maxLevel + 1];
        Expression compiled = new ExpressionBuilder(expression).variable("level").build();
        for (int level = 1; level <= maxLevel; level++) {
            double threshold = compiled.setVariable("level", level).evaluate();
            if (!Double.isFinite(threshold) || threshold <= 0.0D) {
                throw new IllegalArgumentException("XP curve produced non-positive threshold at level " + level);
            }
            thresholds[level] = threshold;
            cumulative[level] = cumulative[level - 1] + threshold;
            if (!Double.isFinite(cumulative[level])) {
                throw new IllegalArgumentException("XP curve cumulative value overflowed at level " + level);
            }
        }
    }

    public String expression() {
        return expression;
    }

    public double xpToNext(int level) {
        if (level <= 0) {
            return thresholds[1];
        }
        if (level >= thresholds.length) {
            return Double.POSITIVE_INFINITY;
        }
        return thresholds[level];
    }

    public double cumulativeForLevel(int level) {
        if (level <= 1) {
            return 0.0D;
        }
        if (level >= cumulative.length) {
            return cumulative[cumulative.length - 1];
        }
        return cumulative[level - 1];
    }

    public int levelForXp(double xp, int maxLevel) {
        int level = 1;
        while (level < maxLevel && xp >= cumulative[level]) {
            level++;
        }
        return level;
    }

    @Override
    public String toString() {
        return expression + " " + Arrays.toString(thresholds);
    }
}
