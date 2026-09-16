package com.Teenkung.devSkills.listener;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.api.XpGainCause;
import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.domain.source.SourceContext;
import com.Teenkung.devSkills.util.AgilityMovement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class SpecialSkillListener implements Listener {

    private final DevSkills plugin;
    private final Map<UUID, SprintState> sprintStates = new LinkedHashMap<>();
    private final NamespacedKey brewOwnerKey;

    public SpecialSkillListener(DevSkills plugin) {
        this.plugin = plugin;
        this.brewOwnerKey = new NamespacedKey(plugin, "brew_owner");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        String key = "DEFAULT";
        if (event.getCaught() instanceof Item item) {
            key = item.getItemStack().getType().name();
        }
        plugin.xpService().grantSourceXp(event.getPlayer(), "fishing", SourceContext.of(SourceCategory.FISH, key), XpGainCause.FISHING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEnchant(EnchantItemEvent event) {
        double scale = Math.max(1, event.getExpLevelCost());
        SourceContext context = SourceContext.scaled(SourceCategory.ENCHANT, "DEFAULT", scale);
        plugin.xpService().grantSourceXp(event.getEnchanter(), "enchanting", context, XpGainCause.ENCHANTING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory() instanceof BrewerInventory inventory)) {
            return;
        }
        boolean ingredientSlot = event.getRawSlot() == 3;
        boolean shiftIntoStand = event.isShiftClick() && event.getClickedInventory() != inventory;
        if (!ingredientSlot && !shiftIntoStand) {
            return;
        }
        watchIngredientChange(inventory, player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory() instanceof BrewerInventory inventory)
                || !event.getRawSlots().contains(3)) {
            return;
        }
        watchIngredientChange(inventory, player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBrewStart(BrewingStartEvent event) {
        Block block = event.getBlock();
        String raw = readBrewOwner(block);
        if (raw != null && parseUuid(raw) == null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> clearBrewOwner(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        String raw = readBrewOwner(block);
        UUID owner = parseUuid(raw);
        if (raw != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> clearBrewOwner(block));
        }
        if (owner == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(owner);
        if (player != null) {
            plugin.xpService().grantSourceXp(player, "alchemy", SourceContext.of(SourceCategory.BREW, "DEFAULT"), XpGainCause.ALCHEMY);
        }
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && movementAllowed(event.getPlayer(), true)) {
            sprintStates.put(event.getPlayer().getUniqueId(), new SprintState(0.0D, System.currentTimeMillis()));
        } else {
            sprintStates.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!movementAllowed(player, player.isSprinting())) {
            sprintStates.remove(player.getUniqueId());
            return;
        }
        SprintState state = sprintStates.computeIfAbsent(player.getUniqueId(), ignored -> new SprintState(0.0D, System.currentTimeMillis()));
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !from.getWorld().equals(to.getWorld())) {
            sprintStates.remove(player.getUniqueId());
            return;
        }
        state.distance += from.distance(to);
        long now = System.currentTimeMillis();
        long elapsed = now - state.lastAwardMillis;
        int requiredMillis = plugin.configManager().settings().agilityAwardSeconds() * 1000;
        if (elapsed >= requiredMillis && state.distance >= plugin.configManager().settings().agilityMinDistance()) {
            SourceContext context = SourceContext.scaled(SourceCategory.MOVEMENT, "DEFAULT", plugin.configManager().settings().agilityXp());
            plugin.xpService().grantSourceXp(player, "agility", context, XpGainCause.AGILITY);
            state.distance = 0.0D;
            state.lastAwardMillis = now;
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        sprintStates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sprintStates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            sprintStates.remove(player.getUniqueId());
        }
    }

    private static final class SprintState {

        private double distance;
        private long lastAwardMillis;

        private SprintState(double distance, long lastAwardMillis) {
            this.distance = distance;
            this.lastAwardMillis = lastAwardMillis;
        }
    }

    private boolean movementAllowed(Player player, boolean sprinting) {
        return AgilityMovement.eligible(
                sprinting,
                player.isSwimming(),
                player.isFlying(),
                player.isGliding(),
                player.isRiptiding(),
                player.isInsideVehicle(),
                player.getGameMode(),
                plugin.configManager().settings().ignoreCreative()
        );
    }

    private void watchIngredientChange(BrewerInventory inventory, Player player) {
        ItemStack current = inventory.getIngredient();
        ItemStack before = current == null ? null : current.clone();
        BrewingStand holder = inventory.getHolder();
        if (holder == null) {
            return;
        }
        Block block = holder.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack after = inventory.getIngredient();
            if (ingredientAdded(before, after)) {
                setBrewOwner(block, player.getUniqueId());
            } else if (empty(after)) {
                clearBrewOwner(block);
            }
        });
    }

    private String readBrewOwner(Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            return null;
        }
        return tileState.getPersistentDataContainer().get(brewOwnerKey, PersistentDataType.STRING);
    }

    private void setBrewOwner(Block block, UUID owner) {
        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }
        tileState.getPersistentDataContainer().set(
                brewOwnerKey,
                PersistentDataType.STRING,
                owner.toString()
        );
        tileState.update();
    }

    private void clearBrewOwner(Block block) {
        if (!(block.getState() instanceof TileState tileState)
                || !tileState.getPersistentDataContainer().has(brewOwnerKey, PersistentDataType.STRING)) {
            return;
        }
        tileState.getPersistentDataContainer().remove(brewOwnerKey);
        tileState.update();
    }

    private boolean ingredientAdded(ItemStack before, ItemStack after) {
        if (empty(after)) {
            return false;
        }
        if (empty(before)) {
            return true;
        }
        if (!before.isSimilar(after)) {
            return true;
        }
        return after.getAmount() > before.getAmount();
    }

    private boolean empty(ItemStack item) {
        return item == null || item.getAmount() <= 0 || item.getType().isAir();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
