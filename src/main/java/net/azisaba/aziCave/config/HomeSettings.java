package net.azisaba.aziCave.config;

import net.azisaba.aziCave.math.BlockBox;
import net.azisaba.aziCave.math.IntVector3;

public record HomeSettings(
        IntVector3 spawn,
        IntVector3 returnSpawn,
        BlockBox area
) {
}
