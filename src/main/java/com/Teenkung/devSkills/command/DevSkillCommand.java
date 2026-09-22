package com.Teenkung.devSkills.command;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.api.XpGainCause;
import com.Teenkung.devSkills.api.booster.XpBoosterRequest;
import com.Teenkung.devSkills.api.booster.XpBoosterScope;
import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.util.DurationParser;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DevSkillCommand implements CommandExecutor, TabCompleter {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.##");

    private final DevSkills plugin;

    public DevSkillCommand(DevSkills plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        execute(sender, label, args);
        return true;
    }

    public void execute(CommandSender sender, String label, String[] args) {
        if (!canUse(sender)) {
            send(sender, "plugin.no-permission");
            return;
        }
        if (args.length == 0) {
            openSkills(sender);
            return;
        }
        if ("skill".equalsIgnoreCase(label) && plugin.configManager().skills().containsKey(args[0])) {
            openSkill(sender, new String[] { "skill", args[0] });
            return;
        }
        switch (args[0].toLowerCase()) {
            case "profile" -> openProfile(sender);
            case "skill" -> openSkill(sender, args);
            case "toggle" -> toggle(sender, args);
            case "reload" -> reload(sender);
            case "admin" -> admin(sender, args);
            default -> openSkills(sender);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!canUse(sender)) {
            return List.of();
        }
        return filter(suggest(sender, alias, args), args.length == 0 ? "" : args[args.length - 1]);
    }

    public Collection<String> suggest(CommandSender sender, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if ("skill".equalsIgnoreCase(alias) && (args.length == 0 || args.length == 1)) {
            suggestions.addAll(plugin.configManager().skills().keySet());
        } else if (args.length == 0 || args.length == 1) {
            suggestions.addAll(List.of("profile", "skill", "toggle"));
            if (sender.hasPermission("devskill.admin")) {
                suggestions.add("reload");
            }
            if (sender.hasPermission("devskill.admin") || canManageBoosters(sender)) {
                suggestions.add("admin");
            }
        } else if (args.length == 2 && "skill".equalsIgnoreCase(args[0])) {
            suggestions.addAll(plugin.configManager().skills().keySet());
        } else if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
            suggestions.addAll(List.of("sound", "actionbar", "bossbar"));
        } else if (args.length == 2 && "admin".equalsIgnoreCase(args[0])
                && (sender.hasPermission("devskill.admin") || canManageBoosters(sender))) {
            if (sender.hasPermission("devskill.admin")) {
                suggestions.addAll(List.of("setlevel", "addxp", "settrait", "reset"));
            }
            if (canManageBoosters(sender)) {
                suggestions.add("booster");
            }
        } else if (args.length == 3 && "admin".equalsIgnoreCase(args[0])
                && (sender.hasPermission("devskill.admin") || canManageBoosters(sender))) {
            if (sender.hasPermission("devskill.admin") && adminActionNeedsPlayer(args[1])) {
                onlinePlayerNames().forEach(suggestions::add);
            } else if ("booster".equalsIgnoreCase(args[1]) && canManageBoosters(sender)) {
                suggestions.addAll(List.of("add", "list", "remove"));
            }
        } else if (args.length == 4 && "admin".equalsIgnoreCase(args[0])
                && sender.hasPermission("devskill.admin") && adminActionNeedsPlayer(args[1])) {
            if ("setlevel".equalsIgnoreCase(args[1]) || "addxp".equalsIgnoreCase(args[1])) {
                suggestions.addAll(plugin.configManager().skills().keySet());
            } else if ("settrait".equalsIgnoreCase(args[1])) {
                suggestions.addAll(plugin.configManager().traits().keySet());
            } else if ("reset".equalsIgnoreCase(args[1])) {
                suggestions.add("all");
                suggestions.addAll(plugin.configManager().skills().keySet());
            }
        } else if (args.length == 3 && boosterCommand(args) && canManageBoosters(sender)) {
            suggestions.addAll(List.of("add", "list", "remove"));
        } else if (args.length == 4 && boosterCommand(args) && canManageBoosters(sender)) {
            if ("add".equalsIgnoreCase(args[2])) {
                suggestions.addAll(List.of("global", "player"));
            } else if ("list".equalsIgnoreCase(args[2])) {
                suggestions.addAll(List.of("all", "global", "player"));
            } else if ("remove".equalsIgnoreCase(args[2])) {
                plugin.boosterService().activeBoosters().stream()
                        .map(booster -> booster.id().toString().substring(0, 8))
                        .forEach(suggestions::add);
            }
        } else if (args.length == 5 && boosterCommand(args) && "add".equalsIgnoreCase(args[2])
                && "player".equalsIgnoreCase(args[3]) && canManageBoosters(sender)) {
            onlinePlayerNames().forEach(suggestions::add);
        } else if (args.length == 5 && boosterCommand(args) && "list".equalsIgnoreCase(args[2])
                && "player".equalsIgnoreCase(args[3]) && canManageBoosters(sender)) {
            onlinePlayerNames().forEach(suggestions::add);
        } else if (args.length >= 4 && boosterCommand(args) && "add".equalsIgnoreCase(args[2]) && canManageBoosters(sender)) {
            int durationIndex = "player".equalsIgnoreCase(args[3]) ? 6 : 5;
            if (args.length == durationIndex + 1) {
                suggestions.addAll(List.of("30m", "1h", "1d", "permanent"));
            }
        }
        return suggestions;
    }

    public Collection<String> suggest(String alias, String[] args) {
        return suggest(Bukkit.getConsoleSender(), alias, args);
    }

    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("devskill.use") || sender.hasPermission("devskill.admin") || canManageBoosters(sender);
    }

    private List<String> filter(Collection<String> suggestions, String token) {
        String lowerToken = token.toLowerCase();
        return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase().startsWith(lowerToken))
                .toList();
    }

    private void openSkills(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        UserProfile profile = profile(player);
        if (profile != null) {
            plugin.menuManager().openSkills(player, profile);
        }
    }

    private void openProfile(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        UserProfile profile = profile(player);
        if (profile != null) {
            plugin.menuManager().openProfile(player, profile);
        }
    }

    private void openSkill(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || !plugin.configManager().skills().containsKey(args[1])) {
            player.sendMessage(plugin.configManager().messageManager().component(locale(player), "plugin.unknown-skill", Map.of("skill", args.length < 2 ? "" : args[1])));
            return;
        }
        UserProfile profile = profile(player);
        if (profile != null) {
            plugin.menuManager().openSkill(player, profile, args[1]);
        }
    }

    private void toggle(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        UserProfile profile = profile(player);
        if (profile == null || args.length < 2) {
            return;
        }
        String setting = args[1].toLowerCase();
        boolean value;
        switch (setting) {
            case "sound", "sounds" -> {
                value = !profile.settings().sounds();
                profile.settings().sounds(value);
            }
            case "actionbar" -> {
                value = !profile.settings().actionbar();
                profile.settings().actionbar(value);
            }
            case "bossbar" -> {
                value = !profile.settings().bossbar();
                profile.settings().bossbar(value);
            }
            default -> {
                return;
            }
        }
        plugin.profileService().markDirty(profile);
        plugin.hudService().settingsChanged(player);
        player.sendMessage(plugin.configManager().messageManager().component(profile.settings().locale(), "plugin.toggle", Map.of("setting", setting, "value", String.valueOf(value))));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("devskill.admin")) {
            send(sender, "plugin.no-permission");
            return;
        }
        plugin.reloadRuntime().whenComplete((ignored, failure) -> onMain(() -> {
            if (failure == null) {
                send(sender, "plugin.reloaded");
                return;
            }
            Throwable cause = rootCause(failure);
            plugin.getLogger().severe("DevSkills reload failed: " + cause.getMessage());
            send(sender, "plugin.reload-failed", Map.of(
                    "error",
                    cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()
            ));
        }));
    }

    private void admin(CommandSender sender, String[] args) {
        if (args.length >= 2 && "booster".equalsIgnoreCase(args[1])) {
            booster(sender, args);
            return;
        }
        if (!sender.hasPermission("devskill.admin")) {
            send(sender, "plugin.no-permission");
            return;
        }
        if (args.length < 2 || !adminAction(args[1])) {
            usage(sender, "/devskill admin <setlevel|addxp|settrait|reset|booster>");
            return;
        }
        if (args.length < 3) {
            usage(sender, adminUsage(args[1]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player must be online: " + args[2]);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "setlevel" -> setLevel(sender, target, args);
            case "addxp" -> addXp(sender, target, args);
            case "settrait" -> setTrait(sender, target, args);
            case "reset" -> reset(sender, target, args);
            default -> usage(sender, "/devskill admin <setlevel|addxp|settrait|reset|booster>");
        }
    }

    private void setLevel(CommandSender sender, Player target, String[] args) {
        if (args.length < 5) {
            usage(sender, "/devskill admin setlevel <player> <skill> <level>");
            return;
        }
        if (!plugin.configManager().skills().containsKey(args[3])) {
            send(sender, "plugin.unknown-skill", Map.of("skill", args[3]));
            return;
        }
        Integer level = integer(sender, args[4]);
        if (level == null) {
            return;
        }
        if (level < 1) {
            send(sender, "plugin.invalid-positive", Map.of("value", args[4]));
            return;
        }
        plugin.xpService().setSkillLevel(target, args[3], level);
        success(sender, target);
    }

    private void addXp(CommandSender sender, Player target, String[] args) {
        if (args.length < 5) {
            usage(sender, "/devskill admin addxp <player> <skill> <amount>");
            return;
        }
        if (!plugin.configManager().skills().containsKey(args[3])) {
            send(sender, "plugin.unknown-skill", Map.of("skill", args[3]));
            return;
        }
        Double amount = decimal(sender, args[4]);
        if (amount == null || amount <= 0.0D) {
            if (amount != null) {
                send(sender, "plugin.invalid-positive", Map.of("value", args[4]));
            }
            return;
        }
        plugin.xpService().grantXp(target, args[3], amount, XpGainCause.COMMAND);
        success(sender, target);
    }

    private void setTrait(CommandSender sender, Player target, String[] args) {
        if (args.length < 5) {
            usage(sender, "/devskill admin settrait <player> <trait> <amount>");
            return;
        }
        if (!plugin.configManager().traits().containsKey(args[3])) {
            send(sender, "plugin.unknown-trait", Map.of("trait", args[3]));
            return;
        }
        Integer amount = integer(sender, args[4]);
        if (amount == null) {
            return;
        }
        if (amount < 0) {
            send(sender, "plugin.invalid-nonnegative", Map.of("value", args[4]));
            return;
        }
        plugin.xpService().setManualTraitLevels(target, args[3], amount);
        success(sender, target);
    }

    private void reset(CommandSender sender, Player target, String[] args) {
        String targetName = args.length >= 4 ? args[3] : "all";
        if (!"all".equalsIgnoreCase(targetName) && !plugin.configManager().skills().containsKey(targetName)) {
            send(sender, "plugin.unknown-skill", Map.of("skill", targetName));
            return;
        }
        plugin.xpService().reset(target, targetName);
        success(sender, target);
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "plugin.player-only");
        return null;
    }

    private UserProfile profile(Player player) {
        UserProfile profile = plugin.profileService().profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            player.sendMessage(plugin.configManager().messageManager().component("en", "plugin.profile-loading"));
        }
        return profile;
    }

    private String locale(Player player) {
        return plugin.profileService().profile(player.getUniqueId()).map(profile -> profile.settings().locale()).orElse("en");
    }

    private Integer integer(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            send(sender, "plugin.invalid-number", Map.of("value", raw));
            return null;
        }
    }

    private Double decimal(CommandSender sender, String raw) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("non-finite number");
            }
            return value;
        } catch (NumberFormatException exception) {
            send(sender, "plugin.invalid-number", Map.of("value", raw));
            return null;
        }
    }

    private void booster(CommandSender sender, String[] args) {
        if (!canManageBoosters(sender)) {
            send(sender, "plugin.no-permission");
            return;
        }
        if (args.length < 3) {
            usage(sender, "/devskill admin booster <add|list|remove>");
            return;
        }
        switch (args[2].toLowerCase()) {
            case "add" -> addBooster(sender, args);
            case "list" -> listBoosters(sender, args);
            case "remove" -> removeBooster(sender, args);
            default -> usage(sender, "/devskill admin booster <add|list|remove>");
        }
    }

    private void addBooster(CommandSender sender, String[] args) {
        if (args.length < 6) {
            usage(sender, "/devskill admin booster add global <multiplier> <duration|permanent> [label...] | player <player|uuid> <multiplier> <duration|permanent> [label...]");
            return;
        }
        String scope = args[3].toLowerCase();
        boolean playerScope = "player".equals(scope);
        if (!playerScope && !"global".equals(scope)) {
            usage(sender, "/devskill admin booster add <global|player> ...");
            return;
        }
        int multiplierIndex = playerScope ? 5 : 4;
        int durationIndex = playerScope ? 6 : 5;
        if (args.length <= durationIndex) {
            usage(sender, playerScope
                    ? "/devskill admin booster add player <player|uuid> <multiplier> <duration|permanent> [label...]"
                    : "/devskill admin booster add global <multiplier> <duration|permanent> [label...]");
            return;
        }
        Double multiplier = decimal(sender, args[multiplierIndex]);
        if (multiplier == null || multiplier <= 1.0D) {
            send(sender, "booster.invalid-multiplier");
            return;
        }
        Instant expiry;
        try {
            expiry = "permanent".equalsIgnoreCase(args[durationIndex])
                    ? null
                    : Instant.now().plus(DurationParser.parse(args[durationIndex]));
        } catch (RuntimeException exception) {
            send(sender, "booster.invalid-duration", Map.of("value", args[durationIndex]));
            return;
        }
        String label = join(args, durationIndex + 1, "EXP Booster");
        if (label.length() > 255) {
            send(sender, "booster.invalid-label");
            return;
        }
        UUID creator = sender instanceof Player player ? player.getUniqueId() : null;
        if (!playerScope) {
            createBooster(sender, XpBoosterRequest.global(multiplier, label, creator, expiry));
            return;
        }
        resolvePlayerId(args[4]).whenComplete((target, failure) -> onMain(() -> {
            if (failure != null || target.isEmpty()) {
                send(sender, "booster.player-not-found", Map.of("player", args[4]));
                return;
            }
            createBooster(sender, XpBoosterRequest.player(target.orElseThrow(), multiplier, label, creator, expiry));
        }));
    }

    private void createBooster(CommandSender sender, XpBoosterRequest request) {
        plugin.boosterService().create(request).whenComplete((booster, failure) -> onMain(() -> {
            if (failure != null) {
                plugin.getLogger().severe("Failed to persist EXP booster: " + rootCause(failure).getMessage());
                send(sender, "booster.storage-failed");
                return;
            }
            send(sender, "booster.activated", boosterPlaceholders(booster));
        }));
    }

    private void listBoosters(CommandSender sender, String[] args) {
        String filter = args.length >= 4 ? args[3].toLowerCase() : "all";
        if ("all".equals(filter)) {
            sendBoosterList(sender, plugin.boosterService().activeBoosters());
            return;
        }
        if ("global".equals(filter)) {
            sendBoosterList(sender, plugin.boosterService().activeGlobalBoosters());
            return;
        }
        if (!"player".equals(filter) || args.length < 5) {
            usage(sender, "/devskill admin booster list [all|global|player <player|uuid>]");
            return;
        }
        resolvePlayerId(args[4]).whenComplete((target, failure) -> onMain(() -> {
            if (failure != null || target.isEmpty()) {
                send(sender, "booster.player-not-found", Map.of("player", args[4]));
                return;
            }
            sendBoosterList(sender, plugin.boosterService().activePlayerBoosters(target.orElseThrow()));
        }));
    }

    private void sendBoosterList(CommandSender sender, List<XpBoosterSnapshot> boosters) {
        if (boosters.isEmpty()) {
            send(sender, "booster.list-empty");
            return;
        }
        send(sender, "booster.list-header", Map.of("count", String.valueOf(boosters.size())));
        for (XpBoosterSnapshot booster : boosters) {
            send(sender, "booster.list-entry", boosterPlaceholders(booster));
        }
    }

    private void removeBooster(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/devskill admin booster remove <id-or-unique-prefix>");
            return;
        }
        plugin.boosterService().removeByIdOrUniquePrefix(args[3]).whenComplete((removed, failure) -> onMain(() -> {
            if (failure != null) {
                Throwable cause = rootCause(failure);
                if (cause instanceof IllegalArgumentException && cause.getMessage() != null && cause.getMessage().contains("ambiguous")) {
                    send(sender, "booster.ambiguous", Map.of("id", args[3]));
                } else {
                    plugin.getLogger().severe("Failed to remove EXP booster: " + cause.getMessage());
                    send(sender, "booster.storage-failed");
                }
                return;
            }
            if (removed.isEmpty()) {
                send(sender, "booster.not-found", Map.of("id", args[3]));
                return;
            }
            send(sender, "booster.removed", Map.of("id", shortId(removed.orElseThrow().id())));
        }));
    }

    private CompletableFuture<Optional<UUID>> resolvePlayerId(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }
        try {
            return CompletableFuture.completedFuture(Optional.of(UUID.fromString(input)));
        } catch (IllegalArgumentException ignored) {
            return plugin.boosterService().findKnownPlayer(input);
        }
    }

    private Map<String, String> boosterPlaceholders(XpBoosterSnapshot booster) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", shortId(booster.id()));
        placeholders.put("label", booster.label().isBlank() ? "EXP Booster" : booster.label());
        placeholders.put("multiplier", NUMBER_FORMAT.format(booster.multiplier()));
        placeholders.put("scope", booster.scope() == XpBoosterScope.GLOBAL
                ? "global"
                : "player:" + booster.targetPlayer().map(UUID::toString).orElse("unknown"));
        placeholders.put("remaining", booster.remainingAt(Instant.now()).map(this::formatDuration).orElse("permanent"));
        return placeholders;
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainingSeconds = seconds % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    private String join(String[] args, int start, String fallback) {
        if (start >= args.length) {
            return fallback;
        }
        String value = String.join(" ", List.of(args).subList(start, args.length)).strip();
        return value.isEmpty() ? fallback : value;
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private boolean boosterCommand(String[] args) {
        return args.length >= 2 && "admin".equalsIgnoreCase(args[0]) && "booster".equalsIgnoreCase(args[1]);
    }

    private boolean adminAction(String action) {
        return "setlevel".equalsIgnoreCase(action)
                || "addxp".equalsIgnoreCase(action)
                || "settrait".equalsIgnoreCase(action)
                || "reset".equalsIgnoreCase(action)
                || "booster".equalsIgnoreCase(action);
    }

    private boolean adminActionNeedsPlayer(String action) {
        return "setlevel".equalsIgnoreCase(action)
                || "addxp".equalsIgnoreCase(action)
                || "settrait".equalsIgnoreCase(action)
                || "reset".equalsIgnoreCase(action);
    }

    private String adminUsage(String action) {
        return switch (action.toLowerCase()) {
            case "setlevel" -> "/devskill admin setlevel <player> <skill> <level>";
            case "addxp" -> "/devskill admin addxp <player> <skill> <amount>";
            case "settrait" -> "/devskill admin settrait <player> <trait> <amount>";
            case "reset" -> "/devskill admin reset <player> [skill|all]";
            case "booster" -> "/devskill admin booster <add|list|remove>";
            default -> "/devskill admin <setlevel|addxp|settrait|reset|booster>";
        };
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean canManageBoosters(CommandSender sender) {
        return sender.hasPermission("devskill.admin") || sender.hasPermission("devskill.admin.booster");
    }

    private void usage(CommandSender sender, String usage) {
        send(sender, "plugin.admin-usage", Map.of("usage", usage));
    }

    private void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String selectedLocale = sender instanceof Player player ? locale(player) : "en";
        sender.sendMessage(plugin.configManager().messageManager().component(selectedLocale, key, placeholders));
    }

    private void onMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void success(CommandSender sender, Player target) {
        send(sender, "plugin.admin-success", Map.of("player", target.getName()));
    }

    @Override
    public @NotNull String toString() {
        return "DevSkillCommand";
    }
}
