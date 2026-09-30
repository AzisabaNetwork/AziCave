package net.azisaba.aziCave.config;

import net.azisaba.aziCave.math.BlockBox;
import net.azisaba.aziCave.math.IntVector3;
import org.bukkit.Material;

public record BossBattleSettings(
        String id,
        String villagerTag,
        int minRound,
        IntVector3 destination,
        float yaw,
        BlockBox returnPortalArea,
        Material returnPortalMaterial
) {
}
