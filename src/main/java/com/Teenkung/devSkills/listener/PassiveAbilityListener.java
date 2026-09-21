package com.Teenkung.devSkills.listener;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.domain.ability.PassiveAbilityAction;
import com.Teenkung.devSkills.service.PassiveAbilityService;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public final class PassiveAbilityListener implements Listener {

    private final DevSkills plugin;
    private final NamespacedKey brewOwnerKey;

    public PassiveAbilityListener(DevSkills plugin) {
        this.plugin = plugin;
        this.brewOwnerKey = new NamespacedKey(plugin, "brew_owner");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        if (isFarming(block)) {
            addExtraDrop(event, player, PassiveAbilityAction.CROP_BONUS_DROP);
            addExperience(event, player, PassiveAbilityAction.CROP_XP);
        }
        if (isLog(material)) {
            addExtraDrop(event, player, PassiveAbilityAction.WOOD_BONUS_DROP);
            addExtraDrop(event, player, PassiveAbilityAction.FORAGING_BONUS_DROP);
        }
        if (Tag.LEAVES.isTagged(material)) {
            addExtraDrop(event, player, PassiveAbilityAction.LEAF_BONUS_DROP);
        }
        if (isMining(material)) {
            addExtraDrop(event, player, PassiveAbilityAction.MINING_BONUS_DROP);
            addExperience(event, player, PassiveAbilityAction.MINING_XP);
            if (triggers(player, PassiveAbilityAction.PICKAXE_HASTE)) {
                addHaste(player, value(player, PassiveAbilityAction.PICKAXE_HASTE));
            }
            if (triggers(player, PassiveAbilityAction.MINING_HUNGER)) {
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
            }
        }
        if (isExcavation(material)) {
            addExtraDrop(event, player, PassiveAbilityAction.EXCAVATION_BONUS_DROP);
            addExperience(event, player, PassiveAbilityAction.EXCAVATION_XP);
            if (triggers(player, PassiveAbilityAction.SHOVEL_HASTE)) {
                addHaste(player, value(player, PassiveAbilityAction.SHOVEL_HASTE));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onGrow(BlockGrowEvent event) {
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        for (Player player : event.getBlock().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(event.getBlock().getLocation()) > 64.0D
                    || !triggers(player, PassiveAbilityAction.CROP_GROWTH)) {
                continue;
            }
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
            event.getNewState().setBlockData(ageable);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player shooter = projectileShooter(event.getDamager());
        if (shooter != null && event.getEntity() instanceof LivingEntity target) {
            addDamage(event, shooter, PassiveAbilityAction.BOW_DAMAGE);
            if (triggers(shooter, PassiveAbilityAction.BOW_STUN)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, true, true, true));
            }
            if (event.getDamager() instanceof Arrow && triggers(shooter, PassiveAbilityAction.BOW_RETURN)) {
                shooter.getInventory().addItem(new ItemStack(Material.ARROW));
            }
            addDamage(event, shooter, PassiveAbilityAction.ENCHANT_DAMAGE);
            return;
        }
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Material tool = attacker.getInventory().getItemInMainHand().getType();
        if (tool.name().endsWith("_AXE")) {
            addDamage(event, attacker, PassiveAbilityAction.AXE_DAMAGE);
        }
        if (tool.name().endsWith("_SWORD")) {
            addDamage(event, attacker, PassiveAbilityAction.MELEE_DAMAGE);
            addDamage(event, attacker, PassiveAbilityAction.ENCHANT_DAMAGE);
            if (isNearFullHealth(target)) {
                addDamage(event, attacker, PassiveAbilityAction.FIRST_HIT);
            }
            if (triggers(attacker, PassiveAbilityAction.BLEED)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0, true, true, true));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getDamage() <= 0.0D) {
            return;
        }
        reduceDamage(event, player, PassiveAbilityAction.DEFENSE_GUARD);
        reduceDamage(event, player, PassiveAbilityAction.MINING_GUARD);
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof LivingEntity && !(causing instanceof Player)) {
            reduceDamage(event, player, PassiveAbilityAction.MOB_GUARD);
        }
        if (isLowHealth(player)) {
            reduceDamage(event, player, PassiveAbilityAction.LOW_HEALTH_GUARD);
        }
        if (event instanceof EntityDamageByEntityEvent && causing instanceof LivingEntity) {
            reduceDamage(event, player, PassiveAbilityAction.PARRY);
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.MAGIC
                || event.getCause() == EntityDamageEvent.DamageCause.POISON
                || event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            reduceDamage(event, player, PassiveAbilityAction.DEBUFF_RESIST);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (event.getCaught() instanceof Item item) {
                if (triggers(player, PassiveAbilityAction.FISH_BONUS_DROP) || triggers(player, PassiveAbilityAction.FISH_TREASURE)) {
                    ItemStack extra = item.getItemStack().clone();
                    extra.setAmount(1);
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
            addFishExperience(event, player, PassiveAbilityAction.FISH_XP);
            return;
        }
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() instanceof LivingEntity target
                && triggers(player, PassiveAbilityAction.FISH_PULL)) {
            Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
            target.setVelocity(pull.normalize().multiply(0.25D).setY(0.15D));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFoodLevel(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getFoodLevel() < player.getFoodLevel()
                && triggers(player, PassiveAbilityAction.HUNGER_SAVE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && triggers(event.getPlayer(), PassiveAbilityAction.SPRINT_SPEED)) {
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, true));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material material = event.getItem().getType();
        if ((material == Material.GOLDEN_APPLE || material == Material.ENCHANTED_GOLDEN_APPLE)
                && triggers(player, PassiveAbilityAction.GOLDEN_HEAL)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, true, true));
        }
        if (material.isEdible() && triggers(player, PassiveAbilityAction.CONSUME_HEAL)) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + 0.5D));
        }
        if (material == Material.POTION && triggers(player, PassiveAbilityAction.POTION_DRINK)) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + 0.5D));
        }
        if (material == Material.POTION && triggers(player, PassiveAbilityAction.POTION_DURATION)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, true, true));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player && triggers(player, PassiveAbilityAction.HEAL_BONUS)) {
            event.setAmount(event.getAmount() * (1.0D + value(player, PassiveAbilityAction.HEAL_BONUS) / 1000.0D));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null
                || !isHarmful(event.getNewEffect().getType())
                || !triggers(player, PassiveAbilityAction.DEBUFF_RESIST)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player player)
                || !triggers(player, PassiveAbilityAction.SPLASH_POWER)) {
            return;
        }
        for (LivingEntity target : event.getAffectedEntities()) {
            event.setIntensity(target, Math.min(1.0D, event.getIntensity(target) + 0.05D));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onLingeringPotion(LingeringPotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof Player player
                && triggers(player, PassiveAbilityAction.LINGERING_POWER)) {
            event.getAreaEffectCloud().setRadius(Math.min(4.0F, event.getAreaEffectCloud().getRadius() + 0.25F));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBrew(BrewEvent event) {
        Player player = brewOwner(event.getBlock());
        if (player == null || !triggers(player, PassiveAbilityAction.BREW_BONUS)) {
            return;
        }
        for (ItemStack item : event.getContents().getContents()) {
            if (item == null || (item.getType() != Material.POTION
                    && item.getType() != Material.SPLASH_POTION
                    && item.getType() != Material.LINGERING_POTION)) {
                continue;
            }
            ItemStack extra = item.clone();
            extra.setAmount(1);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), extra);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (triggers(player, PassiveAbilityAction.ENCHANT_DISCOUNT)) {
            event.setExpLevelCost(Math.max(1, event.getExpLevelCost() - Math.max(1, (int) Math.floor(value(player, PassiveAbilityAction.ENCHANT_DISCOUNT) / 10.0D))));
        }
        if (triggers(player, PassiveAbilityAction.ENCHANT_XP)) {
            player.giveExp(1);
        }
        if (triggers(player, PassiveAbilityAction.ENCHANT_REFUND)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.giveExpLevels(1));
        }
    }

    private void addExtraDrop(BlockBreakEvent event, Player player, PassiveAbilityAction action) {
        if (!event.isDropItems() || !triggers(player, action)) {
            return;
        }
        Block block = event.getBlock();
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        drops.stream().filter(drop -> !drop.getType().isAir()).findFirst().ifPresent(drop -> {
            ItemStack extra = drop.clone();
            extra.setAmount(1);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (block.getType().isAir()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), extra);
                }
            });
        });
    }

    private void addExperience(BlockBreakEvent event, Player player, PassiveAbilityAction action) {
        if (triggers(player, action)) {
            event.setExpToDrop(event.getExpToDrop() + 1);
        }
    }

    private void addFishExperience(PlayerFishEvent event, Player player, PassiveAbilityAction action) {
        if (triggers(player, action)) {
            event.setExpToDrop(event.getExpToDrop() + 1);
        }
    }

    private void addDamage(EntityDamageByEntityEvent event, Player player, PassiveAbilityAction action) {
        if (action == PassiveAbilityAction.ENCHANT_DAMAGE
                && player.getInventory().getItemInMainHand().getEnchantments().isEmpty()) {
            return;
        }
        double bonus = value(player, action);
        if (bonus > 0.0D) {
            event.setDamage(event.getDamage() * (1.0D + bonus / 1000.0D));
        }
    }

    private void reduceDamage(EntityDamageEvent event, Player player, PassiveAbilityAction action) {
        if (triggers(player, action)) {
            event.setDamage(event.getDamage() * (1.0D - Math.min(0.02D, value(player, action) / 1000.0D)));
        }
    }

    private void addHaste(Player player, double value) {
        int amplifier = Math.clamp((int) Math.floor(value / 10.0D) - 1, 0, 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, amplifier, true, false, true));
    }

    private boolean triggers(Player player, PassiveAbilityAction action) {
        return ThreadLocalRandom.current().nextDouble() < Math.min(0.20D, value(player, action) / 100.0D);
    }

    private double value(Player player, PassiveAbilityAction action) {
        return plugin.passiveAbilityService().active(player, action).stream()
                .mapToDouble(PassiveAbilityService.Activation::value)
                .sum();
    }

    private boolean isFarming(Block block) {
        return BlockSourceClassifier.classify(block.getType(), block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge(), true)
                .filter("farming"::equals)
                .isPresent();
    }

    private boolean isMining(Material material) {
        return BlockSourceClassifier.classifyName(material.name(), false, false).filter("mining"::equals).isPresent();
    }

    private boolean isExcavation(Material material) {
        return BlockSourceClassifier.classifyName(material.name(), false, false).filter("excavation"::equals).isPresent();
    }

    private boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material) || material.name().endsWith("_STEM");
    }

    private Player brewOwner(Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            return null;
        }
        String raw = tileState.getPersistentDataContainer().get(brewOwnerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return plugin.getServer().getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isHarmful(PotionEffectType type) {
        return type.equals(PotionEffectType.SLOWNESS)
                || type.equals(PotionEffectType.MINING_FATIGUE)
                || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.POISON)
                || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.BLINDNESS)
                || type.equals(PotionEffectType.HUNGER)
                || type.equals(PotionEffectType.LEVITATION)
                || type.equals(PotionEffectType.DARKNESS)
                || type.equals(PotionEffectType.NAUSEA);
    }

    private Player projectileShooter(Entity entity) {
        if (!(entity instanceof Projectile projectile)) {
            return null;
        }
        ProjectileSource source = projectile.getShooter();
        return source instanceof Player player ? player : null;
    }

    private boolean isNearFullHealth(LivingEntity entity) {
        return entity.getHealth() >= maxHealth(entity) * 0.95D;
    }

    private boolean isLowHealth(Player player) {
        return player.getHealth() <= maxHealth(player) * 0.40D;
    }

    private double maxHealth(LivingEntity entity) {
        return entity.getAttribute(Attribute.MAX_HEALTH).getValue();
    }
}
