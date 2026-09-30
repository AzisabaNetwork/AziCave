package net.azisaba.aziCave.config;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ShopTradeSettings(
        String id,
        Material material,
        int amount,
        long price,
        Set<Material> canDestroy,
        Integer durability,
        PotionType potionType,
        Map<String, Integer> enchantments,
        Map<String, Integer> storedEnchantments,
        String displayName,
        List<String> lore,
        boolean unbreakable
) {
}
