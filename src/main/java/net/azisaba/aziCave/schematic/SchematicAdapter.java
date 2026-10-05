package net.azisaba.aziCave.schematic;

import net.azisaba.aziCave.dungeon.PlacedPiece;
import net.azisaba.aziCave.math.BlockBox;
import org.bukkit.World;

public interface SchematicAdapter {
    boolean isAvailable();

    String describeAvailability();

    void clearCache();

    default boolean supportsAsyncPaste() {
        return false;
    }

    default BlockBox pasteBounds(PlacedPiece piece) throws SchematicPlacementException {
        return piece.worldBounds();
    }

    void paste(World world, PlacedPiece piece) throws SchematicPlacementException;
}
