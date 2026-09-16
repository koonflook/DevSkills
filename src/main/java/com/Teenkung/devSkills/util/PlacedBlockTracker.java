package com.Teenkung.devSkills.util;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlacedBlockTracker {

    private final NamespacedKey key;
    private final int capPerChunk;

    public PlacedBlockTracker(JavaPlugin plugin, int capPerChunk) {
        this.key = new NamespacedKey(plugin, "placed_blocks");
        this.capPerChunk = capPerChunk;
    }

    public void mark(Block block) {
        Chunk chunk = block.getChunk();
        Set<String> positions = read(chunk);
        positions.add(position(block));
        trim(positions);
        write(chunk, positions);
    }

    public boolean isMarked(Block block) {
        return read(block.getChunk()).contains(position(block));
    }

    public boolean unmark(Block block) {
        Chunk chunk = block.getChunk();
        Set<String> positions = read(chunk);
        boolean removed = positions.remove(position(block));
        if (removed) {
            write(chunk, positions);
        }
        return removed;
    }

    private Set<String> read(Chunk chunk) {
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        String raw = container.getOrDefault(key, PersistentDataType.STRING, "");
        Set<String> positions = new LinkedHashSet<>();
        if (raw.isBlank()) {
            return positions;
        }
        String[] parts = raw.split(";");
        for (String part : parts) {
            if (!part.isBlank()) {
                positions.add(part);
            }
        }
        return positions;
    }

    private void write(Chunk chunk, Set<String> positions) {
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        if (positions.isEmpty()) {
            container.remove(key);
            return;
        }
        container.set(key, PersistentDataType.STRING, String.join(";", positions));
    }

    private void trim(Set<String> positions) {
        while (positions.size() > capPerChunk) {
            String first = positions.iterator().next();
            positions.remove(first);
        }
    }

    private String position(Block block) {
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        return localX + "," + block.getY() + "," + localZ;
    }
}
