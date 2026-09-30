package net.azisaba.aziCave.schematic;

import net.azisaba.aziCave.dungeon.PlacedPiece;
import org.bukkit.World;

public interface SchematicAdapter {
    boolean isAvailable();

    String describeAvailability();

    void clearCache();

    void paste(World world, PlacedPiece piece) throws SchematicPlacementException;
}
