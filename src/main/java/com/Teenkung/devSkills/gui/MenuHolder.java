package com.Teenkung.devSkills.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class MenuHolder implements InventoryHolder {

    private final String type;
    private final Map<Integer, MenuAction> actions = new LinkedHashMap<>();
    private Inventory inventory;

    MenuHolder(String type) {
        this.type = type;
    }

    String type() {
        return type;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    Map<Integer, MenuAction> actions() {
        return actions;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
