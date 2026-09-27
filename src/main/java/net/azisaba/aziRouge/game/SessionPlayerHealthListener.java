package net.azisaba.aziRouge.game;

import org.bukkit.Material;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class SessionPlayerHealthListener implements Listener {
    private final GameSessionManager sessionManager;

    public SessionPlayerHealthListener(GameSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) {
            return;
        }
        if (sessionManager.sessionForWorld(player.getWorld()).isEmpty()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getDamageSource().getDamageType() != DamageType.STARVE) {
            return;
        }
        if (sessionManager.sessionForWorld(player.getWorld()).isEmpty()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        GameSession session = sessionManager.sessionForPlayer(player.getUniqueId()).orElse(null);
        if (session != null && session.state() == SessionState.IN_ROUND
                && session.alivePlayers().contains(player.getUniqueId())
                && session.world().getUID().equals(player.getWorld().getUID())) {
            event.setKeepInventory(false);
            event.getItemsToKeep().clear();
            restoreDropsIfEmpty(event.getDrops(), player.getInventory().getContents());
        }
        sessionManager.handlePlayerDeath(player);
    }

    static void restoreDropsIfEmpty(List<ItemStack> drops, ItemStack[] contents) {
        if (!drops.isEmpty()) return;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                drops.add(item.clone());
            }
        }
    }
}
