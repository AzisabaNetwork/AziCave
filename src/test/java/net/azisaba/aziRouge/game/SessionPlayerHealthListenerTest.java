package net.azisaba.aziRouge.game;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SessionPlayerHealthListenerTest {
    @Test
    void restoresSuppressedInventoryDropsWithoutDuplicatingExistingDrops() {
        ItemStack item = new TestItem(Material.DIAMOND);
        List<ItemStack> drops = new ArrayList<>();
        SessionPlayerHealthListener.restoreDropsIfEmpty(drops, new ItemStack[]{null, new TestItem(Material.AIR), item});
        assertEquals(1, drops.size());
        assertEquals(Material.DIAMOND, drops.getFirst().getType());
        assertNotSame(item, drops.getFirst());

        SessionPlayerHealthListener.restoreDropsIfEmpty(drops, new ItemStack[]{new TestItem(Material.STONE)});
        assertEquals(1, drops.size());
    }

    private static final class TestItem extends ItemStack {
        private final Material material;

        private TestItem(Material material) {
            this.material = material;
        }

        @Override
        public Material getType() {
            return material;
        }

        @Override
        public ItemStack clone() {
            return new TestItem(material);
        }
    }
}
