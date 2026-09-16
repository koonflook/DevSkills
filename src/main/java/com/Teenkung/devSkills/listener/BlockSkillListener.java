package com.Teenkung.devSkills.listener;

import com.Teenkung.devSkills.DevSkills;
import com.Teenkung.devSkills.api.XpGainCause;
import com.Teenkung.devSkills.domain.source.SourceCategory;
import com.Teenkung.devSkills.domain.source.SourceContext;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;

public final class BlockSkillListener implements Listener {

    private final DevSkills plugin;

    public BlockSkillListener(DevSkills plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        plugin.placedBlockTracker().mark(event.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        boolean placed = plugin.placedBlockTracker().isMarked(block);
        boolean mature = isMature(block);
        BlockSourceClassifier.classify(material, mature, plugin.configManager().settings().farmingNonAgeableAllowed())
                .filter(skillId -> !placed || (mature && "farming".equals(skillId)))
                .ifPresent(skillId -> grant(event, skillId, material));
        plugin.placedBlockTracker().unmark(block);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (event.getItemsHarvested().isEmpty()) {
            return;
        }
        Block block = event.getHarvestedBlock();
        Material material = block.getType();
        BlockSourceClassifier.classify(material, true, plugin.configManager().settings().farmingNonAgeableAllowed())
                .filter("farming"::equals)
                .ifPresent(ignored -> {
                    SourceContext context = SourceContext.of(SourceCategory.BLOCK, material.name());
                    plugin.xpService().grantSourceXp(event.getPlayer(), "farming", context, XpGainCause.BLOCK_BREAK);
                });
    }

    private void grant(BlockBreakEvent event, String skillId, Material material) {
        SourceContext context = SourceContext.of(SourceCategory.BLOCK, material.name());
        plugin.xpService().grantSourceXp(event.getPlayer(), skillId, context, XpGainCause.BLOCK_BREAK);
    }

    private boolean isMature(Block block) {
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }
}
