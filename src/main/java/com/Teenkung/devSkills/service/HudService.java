package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.config.HudConfig;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.integration.ActionBarBridge;
import com.Teenkung.devSkills.integration.PlaceholderApiBridge;
import com.Teenkung.devSkills.util.MiniMessageUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Coordinates DevSkills action-bar and boss-bar output for one runtime generation. */
public final class HudService implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final HudConfig config;
    private final ProfileService profileService;
    private final ToDoubleFunction<Player> defenseProvider;
    private final PlaceholderApiBridge placeholderApi;
    private final ActionBarBridge actionBarBridge;
    private final Function<UUID, List<XpBoosterSnapshot>> boosterProvider;
    private final ToDoubleFunction<UUID> combinedMultiplierProvider;
    private final Map<UUID, Instant> actionBarSuppressedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, TransientActionBar> transientActionBars = new ConcurrentHashMap<>();
    private final Map<UUID, TransientBossBar> transientBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Set<UUID> actionBarShown = new HashSet<>();
    private final Set<UUID> bossBarShown = new HashSet<>();
    private BukkitTask actionBarTask;
    private BukkitTask bossBarTask;
    private boolean started;

    public HudService(
            JavaPlugin plugin,
            HudConfig config,
            ProfileService profileService,
            ToDoubleFunction<Player> defenseProvider,
            PlaceholderApiBridge placeholderApi,
            ActionBarBridge actionBarBridge,
            Function<UUID, List<XpBoosterSnapshot>> boosterProvider,
            ToDoubleFunction<UUID> combinedMultiplierProvider
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.defenseProvider = Objects.requireNonNull(defenseProvider, "defenseProvider");
        this.placeholderApi = Objects.requireNonNull(placeholderApi, "placeholderApi");
        this.actionBarBridge = Objects.requireNonNull(actionBarBridge, "actionBarBridge");
        this.boosterProvider = Objects.requireNonNull(boosterProvider, "boosterProvider");
        this.combinedMultiplierProvider = Objects.requireNonNull(combinedMultiplierProvider, "combinedMultiplierProvider");
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        actionBarBridge.start(uuid -> suppressActionBar(uuid, ticks(config.externalActionBarHoldTicks())));
        if (!config.enabled()) {
            return;
        }
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshActionBars, 1L, config.refreshTicks());
        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBossBars, 1L, config.boosterRotationTicks());
    }

    /** Cooperatively pauses the constant HUD for integrations that cannot be observed through ProtocolLib. */
    public void suppressActionBar(UUID playerId, Duration duration) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            return;
        }
        Instant requested = Instant.now().plus(duration);
        actionBarSuppressedUntil.merge(playerId, requested, (current, replacement) -> current.isAfter(replacement) ? current : replacement);
    }

    public void showTransientActionBar(Player player, Component component) {
        showTransientActionBar(player, component, config.transientActionBarTicks());
    }

    /** Shows DevSkills-owned transient output; it still yields to an externally observed action bar. */
    public void showTransientActionBar(Player player, Component component, long durationTicks) {
        UserProfile profile = profileService.profile(player.getUniqueId()).orElse(null);
        if (profile == null || !profile.settings().actionbar()) {
            hideActionBar(player);
            return;
        }
        transientActionBars.put(player.getUniqueId(), new TransientActionBar(component, Instant.now().plus(ticks(durationTicks))));
        if (!isActionBarSuppressed(player.getUniqueId(), Instant.now())) {
            sendActionBar(player, component);
        }
    }

    public void showTransientBossBar(Player player, Component title, float progress, BossBar.Color color, long durationTicks) {
        UserProfile profile = profileService.profile(player.getUniqueId()).orElse(null);
        if (profile == null || !profile.settings().bossbar()) {
            hideBossBar(player);
            return;
        }
        Instant expiresAt = Instant.now().plus(ticks(durationTicks));
        transientBossBars.put(player.getUniqueId(), new TransientBossBar(title, clamp(progress), color, expiresAt));
        showBossBar(player, title, clamp(progress), color, BossBar.Overlay.PROGRESS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshBossBar(player, Instant.now()), Math.max(1L, durationTicks));
    }

    /** Applies an actionbar/bossbar toggle immediately rather than waiting for the next refresh. */
    public void settingsChanged(Player player) {
        UserProfile profile = profileService.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            hide(player);
            return;
        }
        if (!profile.settings().actionbar()) {
            transientActionBars.remove(player.getUniqueId());
            hideActionBar(player);
        } else {
            refreshActionBar(player, Instant.now());
        }
        if (!profile.settings().bossbar()) {
            transientBossBars.remove(player.getUniqueId());
            hideBossBar(player);
        } else {
            refreshBossBar(player, Instant.now());
        }
    }

    public void hide(Player player) {
        UUID uuid = player.getUniqueId();
        transientActionBars.remove(uuid);
        transientBossBars.remove(uuid);
        actionBarSuppressedUntil.remove(uuid);
        hideActionBar(player);
        hideBossBar(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
        bossBars.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        if (!started) {
            return;
        }
        started = false;
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        if (bossBarTask != null) {
            bossBarTask.cancel();
            bossBarTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            hide(player);
        }
        bossBars.clear();
        actionBarShown.clear();
        bossBarShown.clear();
        HandlerList.unregisterAll(this);
        actionBarBridge.close();
    }

    private void refreshActionBars() {
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshActionBar(player, now);
        }
    }

    private void refreshActionBar(Player player, Instant now) {
        Optional<UserProfile> optionalProfile = profileService.profile(player.getUniqueId());
        if (optionalProfile.isEmpty() || !optionalProfile.orElseThrow().settings().actionbar()) {
            hideActionBar(player);
            return;
        }
        if (isActionBarSuppressed(player.getUniqueId(), now)) {
            return;
        }
        TransientActionBar transientBar = transientActionBars.get(player.getUniqueId());
        if (transientBar != null && transientBar.expiresAt().isAfter(now)) {
            sendActionBar(player, transientBar.component());
            return;
        }
        transientActionBars.remove(player.getUniqueId());
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("health", decimal(player.getHealth()));
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        placeholders.put("max_health", decimal(maxHealth == null ? player.getHealth() : maxHealth.getValue()));
        placeholders.put("defense", decimal(safeDefense(player)));
        String parsed = placeholderApi.apply(player, config.actionBarFormat(), config.unresolvedPlaceholder());
        sendActionBar(player, MiniMessageUtil.render(parsed, placeholders));
    }

    private void refreshBossBars() {
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshBossBar(player, now);
        }
    }

    private void refreshBossBar(Player player, Instant now) {
        Optional<UserProfile> optionalProfile = profileService.profile(player.getUniqueId());
        if (optionalProfile.isEmpty() || !optionalProfile.orElseThrow().settings().bossbar()) {
            hideBossBar(player);
            return;
        }
        TransientBossBar transientBar = transientBossBars.get(player.getUniqueId());
        if (transientBar != null && transientBar.expiresAt().isAfter(now)) {
            showBossBar(player, transientBar.title(), transientBar.progress(), transientBar.color(), BossBar.Overlay.PROGRESS);
            return;
        }
        transientBossBars.remove(player.getUniqueId());
        if (!config.boosterBossBarEnabled()) {
            hideBossBar(player);
            return;
        }
        List<XpBoosterSnapshot> boosters = safeBoosters(player.getUniqueId());
        if (boosters.isEmpty()) {
            hideBossBar(player);
            return;
        }
        int index = (int) ((System.currentTimeMillis() / Math.max(50L, config.boosterRotationTicks() * 50L)) % boosters.size());
        XpBoosterSnapshot booster = boosters.get(index);
        Map<String, String> placeholders = boosterPlaceholders(booster, boosters.size(), index, player.getUniqueId(), now);
        String parsed = placeholderApi.apply(player, config.boosterBossBarFormat(), config.unresolvedPlaceholder());
        showBossBar(
                player,
                MiniMessageUtil.render(parsed, placeholders),
                boosterProgress(booster, now),
                config.boosterBossBarColor(),
                config.boosterBossBarOverlay()
        );
    }

    private Map<String, String> boosterPlaceholders(XpBoosterSnapshot booster, int count, int index, UUID playerId, Instant now) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("booster_label", booster.label().isBlank() ? booster.scope().name() : booster.label());
        placeholders.put("booster_multiplier", decimal(booster.multiplier()));
        placeholders.put("booster_scope", booster.scope().name().toLowerCase(Locale.ROOT));
        placeholders.put("remaining", booster.remainingAt(now).map(HudService::duration).orElse("Permanent"));
        placeholders.put("index", String.valueOf(index + 1));
        placeholders.put("count", String.valueOf(count));
        placeholders.put("combined_multiplier", decimal(safeCombinedMultiplier(playerId)));
        return placeholders;
    }

    private void showBossBar(Player player, Component title, float progress, BossBar.Color color, BossBar.Overlay overlay) {
        UUID uuid = player.getUniqueId();
        BossBar bossBar = bossBars.computeIfAbsent(uuid, ignored -> BossBar.bossBar(title, progress, color, overlay));
        bossBar.name(title);
        bossBar.progress(clamp(progress));
        bossBar.color(color);
        bossBar.overlay(overlay);
        player.showBossBar(bossBar);
        bossBarShown.add(uuid);
    }

    private void hideBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bossBar = bossBars.get(uuid);
        if (bossBar != null && bossBarShown.remove(uuid)) {
            player.hideBossBar(bossBar);
        }
    }

    private void sendActionBar(Player player, Component component) {
        actionBarBridge.sendInternal(player, component);
        actionBarShown.add(player.getUniqueId());
    }

    private void hideActionBar(Player player) {
        if (actionBarShown.remove(player.getUniqueId())) {
            actionBarBridge.sendInternal(player, Component.empty());
        }
    }

    private boolean isActionBarSuppressed(UUID uuid, Instant now) {
        Instant until = actionBarSuppressedUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (until.isAfter(now)) {
            return true;
        }
        actionBarSuppressedUntil.remove(uuid, until);
        return false;
    }

    private List<XpBoosterSnapshot> safeBoosters(UUID uuid) {
        try {
            List<XpBoosterSnapshot> boosters = boosterProvider.apply(uuid);
            return boosters == null ? List.of() : boosters;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private double safeCombinedMultiplier(UUID uuid) {
        try {
            double value = combinedMultiplierProvider.applyAsDouble(uuid);
            return Double.isFinite(value) && value >= 1.0D ? value : 1.0D;
        } catch (RuntimeException exception) {
            return 1.0D;
        }
    }

    private double safeDefense(Player player) {
        try {
            double value = defenseProvider.applyAsDouble(player);
            return Double.isFinite(value) ? value : 0.0D;
        } catch (RuntimeException | LinkageError exception) {
            return 0.0D;
        }
    }

    private static float boosterProgress(XpBoosterSnapshot booster, Instant now) {
        if (booster.expiresAt().isEmpty()) {
            return 1.0F;
        }
        long total = Duration.between(booster.startsAt(), booster.expiresAt().orElseThrow()).toMillis();
        long remaining = Math.max(0L, Duration.between(now, booster.expiresAt().orElseThrow()).toMillis());
        return total <= 0L ? 0.0F : clamp((float) remaining / total);
    }

    private static String duration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainder = seconds % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainder + "s";
        }
        return remainder + "s";
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

    private static Duration ticks(long ticks) {
        return Duration.ofMillis(Math.max(1L, ticks) * 50L);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record TransientActionBar(Component component, Instant expiresAt) {
    }

    private record TransientBossBar(Component title, float progress, BossBar.Color color, Instant expiresAt) {
    }
}
