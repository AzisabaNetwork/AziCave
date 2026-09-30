package net.azisaba.aziCave.config;

import java.util.List;

public record MobProfileSettings(
        int weight,
        int power,
        double maxHealth,
        double movementSpeed,
        double attackDamage,
        int maxAliveCount,
        MobAiSettings ai,
        List<MobDropEntrySettings> drops
) {
}
