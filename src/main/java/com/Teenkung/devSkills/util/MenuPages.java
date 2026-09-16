package com.Teenkung.devSkills.util;

public final class MenuPages {

    private MenuPages() {
    }

    public static int maxPage(int maxLevel, int pageSize) {
        if (maxLevel <= 0 || pageSize <= 0) {
            return 0;
        }
        return (maxLevel - 1) / pageSize;
    }

    public static int clampPage(int page, int maxLevel, int pageSize) {
        return Math.max(0, Math.min(page, maxPage(maxLevel, pageSize)));
    }

    public static int levelAt(int page, int index, int pageSize) {
        return page * pageSize + index + 1;
    }
}
