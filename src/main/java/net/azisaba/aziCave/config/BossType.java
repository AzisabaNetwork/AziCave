package net.azisaba.aziCave.config;

import java.util.Locale;

public enum BossType {
    NORMAL,
    LAST;

    public static BossType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
