package com.Teenkung.devSkills.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Loaded only after PlaceholderAPI availability has been confirmed. */
final class PlaceholderApiDelegate {

    private PlaceholderApiDelegate() {
    }

    static String apply(Player player, String input) {
        return PlaceholderAPI.setPlaceholders(player, input);
    }

    static AutoCloseable register(JavaPlugin plugin, PlaceholderApiBridge.PlaceholderResolver resolver) {
        Expansion expansion = new Expansion(plugin, resolver);
        if (!expansion.register()) {
            return null;
        }
        return expansion::unregister;
    }

    private static final class Expansion extends PlaceholderExpansion {

        private final JavaPlugin plugin;
        private final PlaceholderApiBridge.PlaceholderResolver resolver;

        private Expansion(JavaPlugin plugin, PlaceholderApiBridge.PlaceholderResolver resolver) {
            this.plugin = plugin;
            this.resolver = resolver;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "devskills";
        }

        @Override
        public @NotNull String getAuthor() {
            return String.join(", ", plugin.getPluginMeta().getAuthors());
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
            if (player == null || player.getUniqueId() == null) {
                return null;
            }
            return resolver.resolve(player.getUniqueId(), params);
        }
    }
}
