package net.azisaba.aziCave.config;

import net.azisaba.aziCave.math.IntVector3;

public record EconomyQuotaSettings(
        long base,
        long perRound,
        double multiplier,
        int maxConsecutiveMisses,
        int warningRemaining,
        IntVector3 deliveryChest
) {
}
