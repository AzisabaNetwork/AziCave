package net.azisaba.aziCave.config;

public record RoundTimingSettings(
        long startTimeTicks,
        long deadlineTimeTicks,
        int minimumSleepingPercentage,
        int sleepDelaySeconds
) {
}
