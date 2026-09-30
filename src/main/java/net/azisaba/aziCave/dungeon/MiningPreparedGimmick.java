package net.azisaba.aziCave.dungeon;

import net.azisaba.aziCave.config.MiningGimmickType;
import net.azisaba.aziCave.math.BlockBox;
import org.bukkit.Location;

import java.util.List;

public record MiningPreparedGimmick(
        MiningGimmickType type,
        Location target,
        PlacedPiece targetPiece,
        List<BlockBox> carveBoxes,
        List<Location> fixedTriggerBlocks,
        List<Location> triggerSideSources
) {
}
