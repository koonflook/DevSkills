package com.Teenkung.devSkills.domain.user;

public final class UserSettings {

    private boolean sounds;
    private boolean actionbar;
    private boolean bossbar;
    private String locale;

    public UserSettings(boolean sounds, boolean actionbar, boolean bossbar, String locale) {
        this.sounds = sounds;
        this.actionbar = actionbar;
        this.bossbar = bossbar;
        this.locale = locale == null || locale.isBlank() ? "en" : locale;
    }

    public static UserSettings defaults() {
        return new UserSettings(true, true, true, "en");
    }

    public boolean sounds() {
        return sounds;
    }

    public void sounds(boolean sounds) {
        this.sounds = sounds;
    }

    public boolean actionbar() {
        return actionbar;
    }

    public void actionbar(boolean actionbar) {
        this.actionbar = actionbar;
    }

    public boolean bossbar() {
        return bossbar;
    }

    public void bossbar(boolean bossbar) {
        this.bossbar = bossbar;
    }

    public String locale() {
        return locale;
    }

    public void locale(String locale) {
        this.locale = locale;
    }
}
