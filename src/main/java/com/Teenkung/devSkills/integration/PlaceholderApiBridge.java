package com.Teenkung.devSkills.integration;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderApiBridge implements AutoCloseable {

    private static final Pattern UNRESOLVED = Pattern.compile("%[A-Za-z0-9_.:-]+%");
    private final JavaPlugin plugin;
    private final PlaceholderResolver resolver;
    private final boolean available;
    private AutoCloseable expansion;

    public PlaceholderApiBridge(JavaPlugin plugin, PlaceholderResolver resolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resolver = resolver;
        this.available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean available() {
        return available;
    }

    public boolean registerExpansion() {
        if (!available || resolver == null || expansion != null) {
            return expansion != null;
        }
        try {
            AutoCloseable candidate = PlaceholderApiDelegate.register(plugin, resolver);
            if (candidate == null) {
                return false;
            }
            expansion = candidate;
            return true;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Unable to register the DevSkills PlaceholderAPI expansion: " + exception.getMessage());
            return false;
        }
    }

    public String apply(Player player, String input) {
        if (input == null || input.isEmpty() || !available) {
            return input == null ? "" : input;
        }
        try {
            return PlaceholderApiDelegate.apply(player, input);
        } catch (RuntimeException | LinkageError exception) {
            if (plugin.getConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().warning("PlaceholderAPI rendering failed for " + player.getName() + ": " + exception.getMessage());
            }
            return input;
        }
    }

    public String apply(Player player, String input, String unresolvedValue) {
        String parsed = apply(player, input);
        String fallback = unresolvedValue == null ? "" : unresolvedValue;
        return UNRESOLVED.matcher(parsed).replaceAll(Matcher.quoteReplacement(fallback));
    }

    @Override
    public void close() {
        if (expansion != null) {
            try {
                expansion.close();
            } catch (Exception | LinkageError exception) {
                if (plugin.getConfig().getBoolean("settings.debug", false)) {
                    plugin.getLogger().warning("Unable to unregister the DevSkills PlaceholderAPI expansion: " + exception.getMessage());
                }
            }
            expansion = null;
        }
    }

    @FunctionalInterface
    public interface PlaceholderResolver {
        @Nullable String resolve(UUID playerId, String identifier);
    }

}
