package net.azisaba.aziCave.config;

import net.azisaba.aziCave.math.BlockBox;

public record PortalDungeonToHomeSettings(
        BlockBox area,
        float destinationYawOffset
) {
}
