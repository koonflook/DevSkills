package com.Teenkung.devSkills.integration;

import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.config.ConfigManager;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.trait.Trait;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.service.LevelerService;
import com.Teenkung.devSkills.service.ProfileService;
import com.Teenkung.devSkills.service.TraitService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.jetbrains.annotations.Nullable;

/** Default resolver for the persistent {@code %devskills_*%} PlaceholderAPI expansion. */
public final class DevSkillsPlaceholderResolver implements PlaceholderApiBridge.PlaceholderResolver {

    private final ConfigManager configManager;
    private final ProfileService profileService;
    private final LevelerService levelerService;
    private final TraitService traitService;
    private final Function<UUID, List<XpBoosterSnapshot>> boosterProvider;
    private final ToDoubleFunction<UUID> combinedMultiplierProvider;

    public DevSkillsPlaceholderResolver(
            ConfigManager configManager,
            ProfileService profileService,
            LevelerService levelerService,
            TraitService traitService,
            Function<UUID, List<XpBoosterSnapshot>> boosterProvider,
            ToDoubleFunction<UUID> combinedMultiplierProvider
    ) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.levelerService = Objects.requireNonNull(levelerService, "levelerService");
        this.traitService = Objects.requireNonNull(traitService, "traitService");
        this.boosterProvider = Objects.requireNonNull(boosterProvider, "boosterProvider");
        this.combinedMultiplierProvider = Objects.requireNonNull(combinedMultiplierProvider, "combinedMultiplierProvider");
    }

    @Override
    public @Nullable String resolve(UUID playerId, String identifier) {
        String normalized = identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
        UserProfile profile = profileService.profile(playerId).orElse(null);
        if (profile == null) {
            return null;
        }
        if (normalized.startsWith("skill_")) {
            return skill(profile, normalized);
        }
        if (normalized.startsWith("trait_")) {
            return trait(profile, normalized);
        }
        List<XpBoosterSnapshot> boosters = safeBoosters(playerId);
        return switch (normalized) {
            case "booster_count" -> String.valueOf(boosters.size());
            case "booster_multiplier" -> decimal(safeMultiplier(playerId));
            case "booster_label" -> boosters.isEmpty() ? "" : boosters.getFirst().label();
            case "booster_remaining", "booster_time_remaining", "booster_remaining_time" -> boosters.isEmpty()
                    ? "0s"
                    : boosters.getFirst().remainingAt(Instant.now()).map(DevSkillsPlaceholderResolver::duration).orElse("Permanent");
            default -> null;
        };
    }

    private @Nullable String skill(UserProfile profile, String identifier) {
        for (Skill skill : configManager.skills().values()) {
            String prefix = "skill_" + skill.id().toLowerCase(Locale.ROOT) + "_";
            if (!identifier.startsWith(prefix)) {
                continue;
            }
            double xp = profile.xp(skill.id());
            return switch (identifier.substring(prefix.length())) {
                case "level" -> String.valueOf(levelerService.level(skill.id(), xp));
                case "xp", "exp" -> decimal(levelerService.xpIntoLevel(skill.id(), xp));
                case "xp_required", "exp_required" -> decimal(levelerService.xpRequiredForCurrentLevel(skill.id(), xp));
                case "progress", "percent" -> decimal(levelerService.progressPercent(skill.id(), xp));
                default -> null;
            };
        }
        return null;
    }

    private @Nullable String trait(UserProfile profile, String identifier) {
        for (Trait trait : configManager.traits().values()) {
            if (identifier.equals("trait_" + trait.id().toLowerCase(Locale.ROOT) + "_level")) {
                return String.valueOf(traitService.traitLevel(profile, trait.id()));
            }
        }
        return null;
    }

    private List<XpBoosterSnapshot> safeBoosters(UUID playerId) {
        try {
            List<XpBoosterSnapshot> boosters = boosterProvider.apply(playerId);
            return boosters == null ? List.of() : boosters;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private double safeMultiplier(UUID playerId) {
        try {
            double value = combinedMultiplierProvider.applyAsDouble(playerId);
            return Double.isFinite(value) && value >= 1.0D ? value : 1.0D;
        } catch (RuntimeException exception) {
            return 1.0D;
        }
    }

    private static String duration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        if (seconds >= 86_400L) {
            return seconds / 86_400L + "d";
        }
        if (seconds >= 3_600L) {
            return seconds / 3_600L + "h";
        }
        if (seconds >= 60L) {
            return seconds / 60L + "m";
        }
        return seconds + "s";
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
