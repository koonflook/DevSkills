package com.Teenkung.devSkills.api.booster;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable request to immediately activate an XP booster.
 *
 * @param scope affected audience
 * @param targetPlayer required for {@link XpBoosterScope#PLAYER}, empty for global boosters
 * @param multiplier displayed multiplier; must be finite and greater than one
 * @param label human-readable booster label
 * @param creator UUID of the player who created the booster, or empty for console/integrations
 * @param expiresAt wall-clock expiry, or empty for a permanent booster
 * @since 1.1
 */
public record XpBoosterRequest(
        XpBoosterScope scope,
        Optional<UUID> targetPlayer,
        double multiplier,
        String label,
        Optional<UUID> creator,
        Optional<Instant> expiresAt
) {

    public XpBoosterRequest {
        scope = Objects.requireNonNull(scope, "scope");
        targetPlayer = Objects.requireNonNull(targetPlayer, "targetPlayer");
        creator = Objects.requireNonNull(creator, "creator");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        label = Objects.requireNonNull(label, "label").strip();
        if (label.length() > 255) {
            throw new IllegalArgumentException("label cannot exceed 255 characters");
        }
        if (!Double.isFinite(multiplier) || multiplier <= 1.0D) {
            throw new IllegalArgumentException("multiplier must be finite and greater than one");
        }
        if (scope == XpBoosterScope.GLOBAL && targetPlayer.isPresent()) {
            throw new IllegalArgumentException("global boosters cannot have a target player");
        }
        if (scope == XpBoosterScope.PLAYER && targetPlayer.isEmpty()) {
            throw new IllegalArgumentException("player boosters require a target player");
        }
    }

    /**
     * Creates a global booster request.
     */
    public static XpBoosterRequest global(double multiplier, String label, UUID creator, Instant expiresAt) {
        return new XpBoosterRequest(
                XpBoosterScope.GLOBAL,
                Optional.empty(),
                multiplier,
                label,
                Optional.ofNullable(creator),
                Optional.ofNullable(expiresAt)
        );
    }

    /**
     * Creates a player-specific booster request.
     */
    public static XpBoosterRequest player(UUID targetPlayer, double multiplier, String label, UUID creator, Instant expiresAt) {
        return new XpBoosterRequest(
                XpBoosterScope.PLAYER,
                Optional.of(Objects.requireNonNull(targetPlayer, "targetPlayer")),
                multiplier,
                label,
                Optional.ofNullable(creator),
                Optional.ofNullable(expiresAt)
        );
    }
}
