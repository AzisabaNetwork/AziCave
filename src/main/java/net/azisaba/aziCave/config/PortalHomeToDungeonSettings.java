package net.azisaba.aziCave.config;

import net.azisaba.aziCave.math.BlockBox;
import net.azisaba.aziCave.math.IntVector3;

public record PortalHomeToDungeonSettings(
        BlockBox area,
        IntVector3 destinationOffset,
        float destinationYawOffset
) {
}
