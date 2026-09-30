package net.azisaba.aziCave.config;

import java.util.Map;

public record MobSpawnLightSettings(
        boolean enabled,
        int sampleAttemptsPerSpawn,
        int maxEffectiveBlockLight,
        double curvePower,
        double minWeight,
        Map<String, TorchSpawnPenaltySettings> torchTypes
) {
}
