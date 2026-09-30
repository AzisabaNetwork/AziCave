package net.azisaba.aziCave.config;

import java.nio.file.Path;

public record SessionSettings(
        int defaultMaxPlayers,
        int maxMaxPlayers,
        int idleTimeoutSeconds,
        String worldNamePrefix,
        Path homeTemplateWorldPath,
        boolean cleanupLeftoverWorldsOnStartup
) {
}
