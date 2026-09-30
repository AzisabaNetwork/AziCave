package net.azisaba.aziCave.config;

public record LeaderboardDisplaySettings(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String title
) {
}
