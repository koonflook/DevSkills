package com.Teenkung.devSkills.util;

public final class ProgressBar {

    private ProgressBar() {
    }

    public static String render(double percent, int width) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(100.0D, percent)) / 100.0D * width);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < width; index++) {
            builder.append(index < filled ? '|' : '.');
        }
        return builder.toString();
    }
}
