package net.azisaba.aziCave.game;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public final class SessionPlayerListener implements Listener {
    private final net.azisaba.aziCave.AziCave plugin;
    private final GameSessionManager sessionManager;

    public SessionPlayerListener(net.azisaba.aziCave.AziCave plugin, GameSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }

        World destinationWorld = event.getTo().getWorld();
        World sourceWorld = event.getFrom().getWorld();
        if (!sessionManager.isSessionWorldEntryAllowed(event.getPlayer(), destinationWorld)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().prefixed("session.error.not-member-world", "&cYou are not a member of that AziCave session."));
            return;
        }
        if (sourceWorld != null && sourceWorld.getUID().equals(destinationWorld.getUID())) {
            return;
        }

        sessionManager.handlePlayerWorldChange(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onObserverTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && event.getTo().getWorld() != null) {
            sessionManager.recordObserverEntry(event.getPlayer(), event.getFrom(), event.getTo().getWorld());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        sessionManager.handleObserverWorldChange(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onObserverGameModeChange(PlayerGameModeChangeEvent event) {
        GameSession session = sessionManager.sessionForWorld(event.getPlayer().getWorld()).orElse(null);
        if (session != null && !session.isMember(event.getPlayer().getUniqueId())
                && event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnLocation().getWorld() == null) {
            return;
        }

        org.bukkit.Location sessionRespawn = sessionManager.respawnLocationFor(event.getPlayer());
        if (sessionRespawn != null) {
            event.setRespawnLocation(sessionRespawn);
        }

        if (!sessionManager.isSessionWorldEntryAllowed(event.getPlayer(), event.getRespawnLocation().getWorld())) {
            GameSession session = sessionManager.sessionForWorld(event.getRespawnLocation().getWorld()).orElse(null);
            if (session != null) {
                org.bukkit.Location fallback = sessionManager.fallbackLocation(session);
                if (fallback != null) {
                    event.setRespawnLocation(fallback);
                }
            }
        }
        sessionManager.handlePlayerWorldChange(event.getPlayer(), event.getPlayer().getLocation(), event.getRespawnLocation());
        sessionManager.handlePlayerRespawn(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sessionManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                sessionManager.switchSpectatorTarget(player);
            }
        });
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        e.setCancelled(true);
    }
}
