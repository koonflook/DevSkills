package com.Teenkung.devSkills.api;

import com.Teenkung.devSkills.api.booster.XpBoosterRequest;
import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.domain.skill.Skill;
import com.Teenkung.devSkills.domain.source.SourceContext;
import com.Teenkung.devSkills.domain.trait.Trait;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Public DevSkills API for integrations.
 *
 * @since 1.0
 */
public interface DevSkillAPI {

    /**
     * Returns a derived skill level from stored XP and the configured level curve.
     *
     * @param player player UUID
     * @param skillId configured skill id
     * @return derived level, or one when the profile is not loaded
     * @since 1.0
     */
    int getSkillLevel(UUID player, String skillId);

    /**
     * Returns raw stored XP for a skill.
     *
     * @param player player UUID
     * @param skillId configured skill id
     * @return stored XP, or zero when the profile is not loaded
     * @since 1.0
     */
    double getSkillXp(UUID player, String skillId);

    /**
     * Adds skill XP through the normal DevSkills gain pipeline.
     *
     * @param player player UUID; player must be online for this implementation
     * @param skillId configured skill id
     * @param amount raw XP amount before DevSkills events and multipliers
     * @param cause source cause shown to event listeners
     * @since 1.0
     */
    void addSkillXp(UUID player, String skillId, double amount, XpGainCause cause);

    /**
     * Sets a skill to the XP floor for a target level.
     *
     * @param player player UUID; player must be online for this implementation
     * @param skillId configured skill id
     * @param level target level
     * @since 1.0
     */
    void setSkillLevel(UUID player, String skillId, int level);

    /**
     * Returns an effective trait level after config base, skill rewards, and manual grants.
     *
     * @param player player UUID
     * @param traitId configured trait id
     * @return effective trait level, or zero when unavailable
     * @since 1.0
     */
    int getTraitLevel(UUID player, String traitId);

    /**
     * Adds persisted manual trait levels.
     *
     * @param player player UUID; player must be online for this implementation
     * @param traitId configured trait id
     * @param amount levels to add, negative values remove manual levels
     * @since 1.0
     */
    void addManualTraitLevels(UUID player, String traitId, int amount);

    /**
     * Returns configured skills.
     *
     * @return live configured skill collection
     * @since 1.0
     */
    Collection<Skill> getSkills();

    /**
     * Returns configured traits.
     *
     * @return live configured trait collection
     * @since 1.0
     */
    Collection<Trait> getTraits();

    /**
     * Registers an in-memory XP source until the next reload.
     *
     * @param skillId configured skill id
     * @param matcher source matcher context
     * @param xp XP amount for matching source contexts
     * @since 1.0
     */
    void registerCustomSource(String skillId, SourceContext matcher, double xp);

    /**
     * Forces a MythicLib stat refresh for a loaded online player profile.
     *
     * @param player player UUID
     * @since 1.0
     */
    void refreshStats(UUID player);

    /**
     * Persists and activates an EXP booster.
     *
     * @param request validated booster request
     * @return completion containing the persisted booster
     * @since 1.1
     */
    CompletionStage<XpBoosterSnapshot> createXpBooster(XpBoosterRequest request);

    /**
     * Removes a persisted EXP booster by its exact identifier.
     *
     * @param boosterId booster identifier
     * @return completion containing whether a row was removed
     * @since 1.1
     */
    CompletionStage<Boolean> removeXpBooster(UUID boosterId);

    /**
     * Returns active global and targeted boosters applicable to a player.
     *
     * @param player player UUID
     * @return immutable active booster snapshots
     * @since 1.1
     */
    Collection<XpBoosterSnapshot> getActiveXpBoosters(UUID player);

    /**
     * Returns the configured additive EXP booster multiplier for a player.
     *
     * @param player player UUID
     * @return multiplier of at least one
     * @since 1.1
     */
    double getEffectiveXpBoosterMultiplier(UUID player);

    /**
     * Cooperatively pauses the constant DevSkills action bar for a player.
     *
     * <p>This is useful for integrations when ProtocolLib is not installed or when the caller
     * wants to reserve a known display window.</p>
     *
     * @param player player UUID
     * @param duration wall-clock suppression duration
     * @since 1.1
     */
    void suppressActionBar(UUID player, Duration duration);
}
