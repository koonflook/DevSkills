package com.Teenkung.devSkills.listener;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.domain.ability.ManaAbilityAction;
import com.Teenkung.devSkills.service.ManaAbilityService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class ManaAbilityListener implements Listener {

    private static final int BLOCKS_PER_TICK = 8;

    private final DevSkills plugin;
    private final NamespacedKey chargedShotMultiplierKey;
    private final Map<UUID, BukkitTask> blockTasks = new HashMap<>();

    public ManaAbilityListener(DevSkills plugin) {
        this.plugin = plugin;
        this.chargedShotMultiplierKey = new NamespacedKey(plugin, "charged_shot_multiplier");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ManaAbilityAction action = activationFor(event.getAction(), event.getMaterial());
        if (action != null) {
            plugin.manaAbilityService().activate(event.getPlayer(), action)
                    .ifPresent(activation -> {
                        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                                && (action == ManaAbilityAction.TREECAPITATOR || action == ManaAbilityAction.TERRAFORM)) {
                            event.setUseInteractedBlock(Event.Result.DENY);
                        }
                        announceActivation(event.getPlayer(), activation);
                    });
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        replantIfActive(player, block);
        if (isTreeLog(block.getType())) {
            scheduleTreecapitator(player, block, block.getType());
        } else if (isTerraformable(block.getType())) {
            scheduleTerraform(player, block, block.getType());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getDamage() <= 0.0D) {
            return;
        }
        plugin.manaAbilityService().active(player, ManaAbilityAction.ABSORPTION).ifPresent(activation -> {
            double reduction = Math.min(0.10D, Math.max(0.0D, activation.value() / 100.0D));
            event.setDamage(event.getDamage() * (1.0D - reduction));
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        plugin.manaAbilityService().consumeActive(player, ManaAbilityAction.CHARGED_SHOT).ifPresent(activation ->
                event.getProjectile().getPersistentDataContainer().set(
                        chargedShotMultiplierKey,
                        PersistentDataType.DOUBLE,
                        1.0D + activation.value() / 100.0D
                )
        );
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && event.getEntity() instanceof LivingEntity target
                && player.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")) {
            plugin.manaAbilityService().consumeActive(player, ManaAbilityAction.LIGHTNING_BLADE)
                    .ifPresent(ignored -> target.getWorld().strikeLightningEffect(target.getLocation()));
        }
        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }
        Double multiplier = projectile.getPersistentDataContainer().get(chargedShotMultiplierKey, PersistentDataType.DOUBLE);
        if (multiplier == null) {
            return;
        }
        projectile.getPersistentDataContainer().remove(chargedShotMultiplierKey);
        event.setDamage(event.getDamage() * multiplier);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || !(event.getCaught() instanceof org.bukkit.entity.LivingEntity target)) {
            return;
        }
        plugin.manaAbilityService().consumeActive(event.getPlayer(), ManaAbilityAction.SHARP_HOOK)
                .ifPresent(activation -> target.damage(activation.value(), event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelBlockTask(event.getPlayer().getUniqueId());
        plugin.manaAbilityService().clear(event.getPlayer());
    }

    private void replantIfActive(Player player, Block block) {
        if (plugin.manaAbilityService().active(player, ManaAbilityAction.REPLENISH).isEmpty()
                || !(block.getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        Material crop = block.getType();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() != Material.AIR) {
                return;
            }
            block.setType(crop, false);
            if (block.getBlockData() instanceof Ageable replanted) {
                replanted.setAge(0);
                block.setBlockData(replanted, false);
            }
        });
    }

    private void scheduleTreecapitator(Player player, Block origin, Material material) {
        List<BlockTarget> targets = collectTreeBlocks(origin, material, plugin.configManager().manaAbilities()
                .values().stream()
                .filter(config -> config.action() == ManaAbilityAction.TREECAPITATOR)
                .findFirst()
                .map(config -> config.maxBlocks())
                .orElse(1));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.manaAbilityService().consumeActive(player, ManaAbilityAction.TREECAPITATOR).ifPresent(activation ->
                    scheduleBlockBatch(player, targets)
            );
        });
    }

    private void scheduleTerraform(Player player, Block origin, Material material) {
        List<BlockTarget> targets = collectTerraformBlocks(origin, material, plugin.configManager().manaAbilities()
                .values().stream()
                .filter(config -> config.action() == ManaAbilityAction.TERRAFORM)
                .findFirst()
                .map(config -> config.maxBlocks())
                .orElse(1));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.manaAbilityService().consumeActive(player, ManaAbilityAction.TERRAFORM).ifPresent(activation ->
                    scheduleBlockBatch(player, targets)
            );
        });
    }

    private void scheduleBlockBatch(Player player, List<BlockTarget> targets) {
        if (targets.isEmpty()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        cancelBlockTask(playerId);
        Deque<BlockTarget> pending = new ArrayDeque<>(targets);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    blockTasks.remove(playerId);
                    return;
                }
                for (int processed = 0; processed < BLOCKS_PER_TICK && !pending.isEmpty(); processed++) {
                    breakAdditionalBlock(player, pending.removeFirst());
                }
                if (pending.isEmpty()) {
                    cancel();
                    blockTasks.remove(playerId);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        blockTasks.put(playerId, task);
    }

    private void breakAdditionalBlock(Player player, BlockTarget target) {
        if (target.block().getType() != target.material()) {
            return;
        }
        BlockBreakEvent breakEvent = new BlockBreakEvent(target.block(), player);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (!breakEvent.isCancelled()) {
            target.block().breakNaturally(player.getInventory().getItemInMainHand());
        }
    }

    private List<BlockTarget> collectTreeBlocks(Block origin, Material material, int maxBlocks) {
        int maximum = Math.max(0, maxBlocks - 1);
        List<BlockTarget> blocks = new ArrayList<>(maximum);
        Deque<Block> pending = new ArrayDeque<>();
        Set<BlockPosition> visited = new HashSet<>();
        pending.add(origin);
        visited.add(position(origin));
        while (!pending.isEmpty() && blocks.size() < maximum) {
            Block current = pending.removeFirst();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        Block next = current.getRelative(x, y, z);
                        if (!withinTreeBounds(origin, next) || !isTreeLog(next.getType()) || !visited.add(position(next))) {
                            continue;
                        }
                        blocks.add(new BlockTarget(next, next.getType()));
                        pending.addLast(next);
                        if (blocks.size() >= maximum) {
                            break;
                        }
                    }
                    if (blocks.size() >= maximum) {
                        break;
                    }
                }
                if (blocks.size() >= maximum) {
                    break;
                }
            }
        }
        return blocks;
    }

    private List<BlockTarget> collectTerraformBlocks(Block origin, Material material, int maxBlocks) {
        int maximum = Math.max(0, maxBlocks - 1);
        List<BlockTarget> blocks = new ArrayList<>(maximum);
        Deque<Block> pending = new ArrayDeque<>();
        Set<BlockPosition> visited = new HashSet<>();
        pending.add(origin);
        visited.add(position(origin));
        while (!pending.isEmpty() && blocks.size() < maximum) {
            Block current = pending.removeFirst();
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Block next = current.getRelative(face);
                if (next.getType() != material || !visited.add(position(next))) {
                    continue;
                }
                blocks.add(new BlockTarget(next, material));
                pending.addLast(next);
                if (blocks.size() >= maximum) {
                    break;
                }
            }
        }
        return blocks;
    }

    private ManaAbilityAction activationFor(Action action, Material material) {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return null;
        }
        if (material == Material.FISHING_ROD) {
            return ManaAbilityAction.SHARP_HOOK;
        }
        if (material == Material.BOW || material == Material.CROSSBOW) {
            return ManaAbilityAction.CHARGED_SHOT;
        }
        String name = material.name();
        if (name.endsWith("_HOE")) {
            return ManaAbilityAction.REPLENISH;
        }
        if (name.endsWith("_AXE")) {
            return ManaAbilityAction.TREECAPITATOR;
        }
        if (name.endsWith("_PICKAXE")) {
            return ManaAbilityAction.SPEED_MINE;
        }
        if (name.endsWith("_SHOVEL")) {
            return ManaAbilityAction.TERRAFORM;
        }
        if (name.endsWith("_SWORD")) {
            return ManaAbilityAction.LIGHTNING_BLADE;
        }
        if (material == Material.SHIELD) {
            return ManaAbilityAction.ABSORPTION;
        }
        return null;
    }

    private void announceActivation(Player player, ManaAbilityService.Activation activation) {
        String locale = "th";
        String abilityName = plugin.configManager().messageManager().raw(
                locale,
                "mana-ability.names." + activation.config().id(),
                Map.of()
        );
        player.sendMessage(plugin.configManager().messageManager().component(
                locale,
                "mana-ability.activated",
                Map.of("ability", abilityName)
        ));
    }

    private boolean isTreeLog(Material material) {
        return Tag.LOGS.isTagged(material) || material.name().endsWith("_STEM");
    }

    private boolean isTerraformable(Material material) {
        return BlockSourceClassifier.classifyName(material.name(), false, false)
                .filter("excavation"::equals)
                .isPresent();
    }

    private boolean withinTreeBounds(Block origin, Block block) {
        return Math.abs(block.getX() - origin.getX()) <= 6
                && Math.abs(block.getZ() - origin.getZ()) <= 6
                && Math.abs(block.getY() - origin.getY()) <= 31;
    }

    private void cancelBlockTask(UUID playerId) {
        BukkitTask task = blockTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private BlockPosition position(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    private record BlockPosition(int x, int y, int z) {
    }

    private record BlockTarget(Block block, Material material) {
    }
}
