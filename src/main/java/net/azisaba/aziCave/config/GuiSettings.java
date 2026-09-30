package net.azisaba.aziCave.config;

import java.util.List;

public record GuiSettings(
        List<DangerLevel> dangerLevels,
        int defaultDepth
) {
    public record DangerLevel(String desc, int depth) {
    }

    public String describe(int depth) {
        return dangerLevels.stream()
                .filter(level -> level.depth() == depth)
                .map(DangerLevel::desc)
                .findFirst()
                .orElse(String.valueOf(depth));
    }
}
