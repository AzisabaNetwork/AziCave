package net.azisaba.aziCave.config;

import org.bukkit.Material;

import java.util.Set;

public record TorchSpawnPenaltySettings(
        String key,
        Set<Material> materials,
        int radius,
        double multiplier
) {
}
