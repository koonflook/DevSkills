package com.Teenkung.devSkills.api;

public final class DevSkillsProvider {

    private static DevSkillAPI api;

    private DevSkillsProvider() {
    }

    public static DevSkillAPI get() {
        if (api == null) {
            throw new IllegalStateException("DevSkills API is not available");
        }
        return api;
    }

    public static void set(DevSkillAPI api) {
        DevSkillsProvider.api = api;
    }

    public static void clear(DevSkillAPI api) {
        if (DevSkillsProvider.api == api) {
            DevSkillsProvider.api = null;
        }
    }
}
