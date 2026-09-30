package net.azisaba.aziCave.config;

import java.util.List;

public record ShopSettings(
        String title,
        List<ShopTradeSettings> trades
) {
}
