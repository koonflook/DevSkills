package com.Teenkung.devSkills.integration;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtocolLibActionBarBridge implements ActionBarBridge {

    private final JavaPlugin plugin;
    private final boolean available;
    private final Set<UUID> internalSends = ConcurrentHashMap.newKeySet();
    private ProtocolManager protocolManager;
    private PacketListener listener;

    public ProtocolLibActionBarBridge(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.available = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    }

    public boolean available() {
        return available;
    }

    public boolean start(Consumer<UUID> externalActionBarObserver) {
        Objects.requireNonNull(externalActionBarObserver, "externalActionBarObserver");
        if (!available || listener != null) {
            return listener != null;
        }
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            listener = new PacketAdapter(
                    plugin,
                    ListenerPriority.MONITOR,
                    PacketType.Play.Server.SET_ACTION_BAR_TEXT,
                    PacketType.Play.Server.SYSTEM_CHAT
            ) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null || internalSends.contains(player.getUniqueId()) || !isActionBar(event)) {
                        return;
                    }
                    externalActionBarObserver.accept(player.getUniqueId());
                }
            };
            protocolManager.addPacketListener(listener);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            listener = null;
            protocolManager = null;
            plugin.getLogger().warning("ProtocolLib action-bar arbitration is unavailable: " + exception.getMessage());
            return false;
        }
    }

    public void sendInternal(Player player, Component component) {
        UUID uuid = player.getUniqueId();
        internalSends.add(uuid);
        try {
            player.sendActionBar(component);
        } finally {
            internalSends.remove(uuid);
        }
    }

    @Override
    public void close() {
        if (protocolManager != null && listener != null) {
            try {
                protocolManager.removePacketListener(listener);
            } catch (RuntimeException | LinkageError exception) {
                if (plugin.getConfig().getBoolean("settings.debug", false)) {
                    plugin.getLogger().warning("Unable to remove ProtocolLib action-bar listener: " + exception.getMessage());
                }
            }
        }
        listener = null;
        protocolManager = null;
        internalSends.clear();
    }

    private boolean isActionBar(PacketEvent event) {
        if (PacketType.Play.Server.SET_ACTION_BAR_TEXT.equals(event.getPacketType())) {
            return true;
        }
        if (!PacketType.Play.Server.SYSTEM_CHAT.equals(event.getPacketType())) {
            return false;
        }
        Boolean overlay = event.getPacket().getBooleans().readSafely(0);
        return Boolean.TRUE.equals(overlay);
    }
}
