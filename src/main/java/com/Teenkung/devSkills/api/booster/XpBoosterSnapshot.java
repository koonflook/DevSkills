package com.Teenkung.devSkills.api.booster;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable persisted view of an XP booster.
 *
 * @param id stable booster identifier
 * @param scope affected audience
 * @param targetPlayer target UUID for player boosters
 * @param multiplier displayed multiplier
 * @param label human-readable label
 * @param creator creator UUID, if known
 * @param startsAt wall-clock activation time
 * @param expiresAt wall-clock expiry, or empty when permanent
 * @since 1.1
 */
public record XpBoosterSnapshot(
        UUID id,
        XpBoosterScope scope,
        Optional<UUID> targetPlayer,
        double multiplier,
        String label,
        Optional<UUID> creator,
        Instant startsAt,
        Optional<Instant> expiresAt
) {

    public XpBoosterSnapshot {
        id = Objects.requireNonNull(id, "id");
        scope = Objects.requireNonNull(scope, "scope");
        targetPlayer = Objects.requireNonNull(targetPlayer, "targetPlayer");
        creator = Objects.requireNonNull(creator, "creator");
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
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
        if (expiresAt.isPresent() && !expiresAt.orElseThrow().isAfter(startsAt)) {
            throw new IllegalArgumentException("expiresAt must be after startsAt");
        }
    }

    /**
     * Returns whether this booster applies at the supplied wall-clock time.
     */
    public boolean activeAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !startsAt.isAfter(instant) && expiresAt.map(expiry -> expiry.isAfter(instant)).orElse(true);
    }

    /**
     * Additive contribution used when combining boosters.
     */
    public double additiveContribution() {
        return multiplier - 1.0D;
    }

    /**
     * Remaining wall-clock duration, empty for permanent boosters.
     */
    public Optional<Duration> remainingAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return expiresAt.map(expiry -> Duration.between(instant, expiry).isNegative()
                ? Duration.ZERO
                : Duration.between(instant, expiry));
    }
}
