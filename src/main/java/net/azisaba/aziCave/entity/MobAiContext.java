package net.azisaba.aziCave.entity;

import net.azisaba.aziCave.AziCave;
import net.azisaba.aziCave.config.MobProfileSettings;

public record MobAiContext(
        AziCave plugin,
        MobProfile profile,
        MobProfileSettings settings
) {
}
