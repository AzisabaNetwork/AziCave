package net.azisaba.aziCave.config;

import org.bukkit.Material;

import java.util.Map;

public record EconomySettings(
        long initialBalance,
        EconomyQuotaSettings quota,
        Map<Material, Long> sellPrices
) {
}
