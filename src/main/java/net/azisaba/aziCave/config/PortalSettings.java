package net.azisaba.aziCave.config;

public record PortalSettings(
        PortalHomeToDungeonSettings homeToDungeon,
        PortalDungeonToHomeSettings dungeonToHome,
        int cooldownSeconds
) {
}
