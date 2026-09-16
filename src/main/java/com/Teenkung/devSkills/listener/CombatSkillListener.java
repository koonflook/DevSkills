package com.Teenkung.devSkills.listener;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.api.XpGainCause;
import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.domain.source.SourceContext;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatSkillListener implements Listener {

    private final DevSkills plugin;

    public CombatSkillListener(DevSkills plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player projectileShooter = projectileShooter(byEntity.getDamager());
            if (projectileShooter != null && event.getEntity() instanceof LivingEntity target
                    && !target.getUniqueId().equals(projectileShooter.getUniqueId())) {
                SourceContext context = SourceContext.of(SourceCategory.ENTITY, target.getType().name());
                plugin.xpService().grantSourceXp(projectileShooter, "archery", context, XpGainCause.COMBAT);
            }
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity causingEntity = event.getDamageSource().getCausingEntity();
        if (causingEntity != null && !causingEntity.getUniqueId().equals(player.getUniqueId())) {
            SourceContext context = SourceContext.of(SourceCategory.ENTITY, causingEntity.getType().name());
            plugin.xpService().grantSourceXp(player, "defense", context, XpGainCause.DEFENSE);
        } else if (causingEntity == null && plugin.configManager().settings().defenseEnvironmental()) {
            SourceContext context = SourceContext.of(SourceCategory.DAMAGE, event.getCause().name());
            plugin.xpService().grantSourceXp(player, "defense", context, XpGainCause.DEFENSE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        SourceContext context = SourceContext.of(SourceCategory.ENTITY, event.getEntityType().name());
        plugin.xpService().grantSourceXp(killer, "fighting", context, XpGainCause.COMBAT);
    }

    private Player projectileShooter(Entity damager) {
        if (!(damager instanceof Projectile projectile)) {
            return null;
        }
        ProjectileSource source = projectile.getShooter();
        if (source instanceof Player player) {
            return player;
        }
        return null;
    }
}
