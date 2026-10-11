package net.azisaba.aziCave.dungeon;

import net.azisaba.aziCave.AziCave;
import net.azisaba.aziCave.config.TrapDefinitionSettings;
import net.azisaba.aziCave.config.TrapSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class TrapPopulator {
    private static final String LANDMINE_KEY = "landmine";
    private static final float TNT_MARKER_SCALE = 0.4F;
    private static final float PRESSURE_PLATE_HEIGHT = 1.0F / 16.0F;

    private final AziCave plugin;

    public TrapPopulator(AziCave plugin) {
        this.plugin = plugin;
    }

    public int populateRoom(World world, PlacedPiece piece) {
        TrapSettings settings = plugin.settings().azicave().traps();
        Random random = ThreadLocalRandom.current();
        double spawnChance = settings.spawnChance(piece.depth());
        double roll = random.nextDouble();
        if (roll > spawnChance) {
            plugin.debugLogger().log("trap", "skipped_chance", Map.of(
                    "chance", spawnChance,
                    "depth", piece.depth(),
                    "piece", piece.template().id(),
                    "roll", roll
            ));
            return 0;
        }
        return populateGuaranteedRoom(world, piece, random);
    }

    public int populateGuaranteedRoom(World world, PlacedPiece piece, Random random) {
        TrapSettings settings = plugin.settings().azicave().traps();
        TrapDefinitionSettings definition = settings.selectRandomDefinition(random, piece.depth());
        if (definition == null) {
            plugin.debugLogger().log("trap", "no_definition", Map.of(
                    "depth", piece.depth(),
                    "definitions", settings.definitions().size(),
                    "piece", piece.template().id()
            ));
            return 0;
        }

        Block placementBlock = RoomPlacementHelper.findPlacementBlock(world, piece.worldBounds(), random);
        if (placementBlock == null) {
            plugin.debugLogger().log("trap", "no_placement", Map.of(
                    "bounds", piece.worldBounds(),
                    "definition", definition.key(),
                    "depth", piece.depth(),
                    "diagnostic", RoomPlacementHelper.diagnosePlacementFailure(world, piece.worldBounds()),
                    "piece", piece.template().id(),
                    "world", world.getName()
            ));
            return 0;
        }

        Material material = Material.matchMaterial(definition.displayMaterial());
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Invalid trap display material: " + definition.displayMaterial());
            return 0;
        }

        Location displayLocation = placementBlock.getLocation();
        if (!(world.spawnEntity(displayLocation, EntityType.BLOCK_DISPLAY) instanceof BlockDisplay display)) {
            plugin.getLogger().warning("Failed to create trap display at " + displayLocation);
            return 0;
        }

        display.setBlock(material.createBlockData());
        display.setGravity(false);
        display.setInvulnerable(true);
        display.addScoreboardTag(DisplayGimmickKeys.TRAP_DISPLAY_TAG);
        display.getPersistentDataContainer().set(
                DisplayGimmickKeys.trapKey(plugin),
                PersistentDataType.STRING,
                definition.key()
        );
        if (LANDMINE_KEY.equals(definition.key())) {
            spawnTntMarker(world, displayLocation, display);
        }
        plugin.debugLogger().log("trap", "spawned", Map.of(
                "definition", definition.key(),
                "depth", piece.depth(),
                "material", material,
                "piece", piece.template().id(),
                "x", placementBlock.getX(),
                "y", placementBlock.getY(),
                "z", placementBlock.getZ()
        ));
        return 1;
    }

    private void spawnTntMarker(World world, Location location, BlockDisplay trapDisplay) {
        if (!(world.spawnEntity(location, EntityType.BLOCK_DISPLAY) instanceof BlockDisplay marker)) {
            plugin.getLogger().warning("Failed to create landmine TNT display at " + location);
            return;
        }
        float offset = (1.0F - TNT_MARKER_SCALE) / 2.0F;
        marker.setBlock(Material.TNT.createBlockData());
        marker.setGravity(false);
        marker.setInvulnerable(true);
        marker.setTransformation(new Transformation(
                new Vector3f(offset, PRESSURE_PLATE_HEIGHT, offset),
                new AxisAngle4f(),
                new Vector3f(TNT_MARKER_SCALE, TNT_MARKER_SCALE, TNT_MARKER_SCALE),
                new AxisAngle4f()
        ));
        trapDisplay.getPersistentDataContainer().set(
                DisplayGimmickKeys.linkedDisplayKey(plugin),
                PersistentDataType.STRING,
                marker.getUniqueId().toString()
        );
    }
}
