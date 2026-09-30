package net.azisaba.aziCave.config;

public record AziCaveSettings(
        MobSpawnSettings mobSpawn,
        ChestSettings chest,
        TreasureSettings treasure,
        TrapSettings traps,
        MiningSettings mining
) {
}
