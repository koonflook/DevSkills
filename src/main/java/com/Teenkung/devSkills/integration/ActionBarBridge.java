package com.Teenkung.devSkills.integration;

import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Dependency-free action-bar transport used when ProtocolLib is not installed. */
public interface ActionBarBridge extends AutoCloseable {

    boolean start(Consumer<UUID> externalActionBarObserver);

    void sendInternal(Player player, Component component);

    @Override
    void close();

    static ActionBarBridge direct() {
        return new ActionBarBridge() {
            @Override
            public boolean start(Consumer<UUID> externalActionBarObserver) {
                return false;
            }

            @Override
            public void sendInternal(Player player, Component component) {
                player.sendActionBar(component);
            }

            @Override
            public void close() {
            }
        };
    }
}
