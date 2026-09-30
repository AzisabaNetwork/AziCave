package net.azisaba.aziCave.dungeon;

import net.azisaba.aziCave.math.IntVector3;
import org.bukkit.World;

import java.util.List;

public record GenerationExecutionRequest(
        List<String> templatePatterns,
        String startPieceId,
        World world,
        IntVector3 origin,
        long seed,
        Integer maxDepthOverride
) {
}
