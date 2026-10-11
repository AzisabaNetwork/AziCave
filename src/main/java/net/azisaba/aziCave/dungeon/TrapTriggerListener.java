package net.azisaba.aziCave.dungeon;

import net.azisaba.aziCave.AziCave;
import net.azisaba.aziCave.config.TrapDefinitionSettings;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class TrapTriggerListener implements Listener {
    private final AziCave plugin;

    public TrapTriggerListener(AziCave plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (event.getFrom().toVector().distanceSquared(event.getTo().toVector()) < 1.0E-6D) {
            return;
        }

        for (Entity entity : event.getTo().getWorld().getNearbyEntities(event.getTo(), 1.75D, 1.5D, 1.75D)) {
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }
            if (!display.getScoreboardTags().contains(DisplayGimmickKeys.TRAP_DISPLAY_TAG)) {
                continue;
            }

            String trapKey = display.getPersistentDataContainer().get(
                    DisplayGimmickKeys.trapKey(plugin),
                    PersistentDataType.STRING
            );
            if (trapKey == null) {
                removeTrap(display);
                continue;
            }

            TrapDefinitionSettings definition = plugin.settings().azicave().traps().definition(trapKey);
            if (definition == null) {
                removeTrap(display);
                continue;
            }

            Location trapCenter = display.getLocation().clone().add(0.5D, 0.1D, 0.5D);
            if (trapCenter.distanceSquared(event.getTo()) > definition.triggerRadius() * definition.triggerRadius()) {
                continue;
            }

            removeTrap(display);
            event.getPlayer().getWorld().createExplosion(
                    trapCenter.getX(),
                    trapCenter.getY(),
                    trapCenter.getZ(),
                    definition.explosionPower(),
                    definition.setFire(),
                    definition.breakBlocks(),
                    null
            );
            break;
        }
    }

    private void removeTrap(BlockDisplay display) {
        String linkedId = display.getPersistentDataContainer().get(
                DisplayGimmickKeys.linkedDisplayKey(plugin),
                PersistentDataType.STRING
        );
        if (linkedId != null) {
            try {
                Entity linked = display.getWorld().getEntity(UUID.fromString(linkedId));
                if (linked != null) {
                    linked.remove();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        display.remove();
    }
}
