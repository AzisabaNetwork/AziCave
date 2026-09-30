package net.azisaba.aziCave.dungeon;

import net.azisaba.aziCave.math.BlockBox;
import net.azisaba.aziCave.math.IntVector3;
import net.azisaba.aziCave.math.Rotation;
import net.azisaba.aziCave.template.PieceTemplate;

import java.util.List;

public record PlacedPiece(
        int index,
        PieceTemplate template,
        Rotation rotation,
        IntVector3 origin,
        int depth,
        BlockBox worldBounds,
        List<IntVector3> strollPoints,
        List<PlacedEntrance> entrances
) {
}
