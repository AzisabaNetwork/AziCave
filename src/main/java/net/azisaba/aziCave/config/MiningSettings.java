package net.azisaba.aziCave.config;

import org.bukkit.Material;

import java.util.List;

public record MiningSettings(
        boolean enabled,
        int oresPerPiece,
        int triggersPerDungeon,
        double triggersPerDepth,
        int maxTriggersPerDungeon,
        Material backingMaterial,
        Material triggerMaterial,
        int minTriggerGimmickDistance,
        int chainStepTicks,
        List<MiningOreSettings> ores,
        List<MiningGimmickSettings> gimmicks
) {
    public int triggersForDepth(int depth) {
        double scaled = triggersPerDungeon + Math.max(0, depth) * triggersPerDepth;
        return (int) Math.clamp(Math.floor(scaled), 0.0D, (double) maxTriggersPerDungeon);
    }
}
