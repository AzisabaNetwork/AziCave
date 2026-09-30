package net.azisaba.aziCave.author;

import net.azisaba.aziCave.math.BlockBox;
import net.azisaba.aziCave.math.IntVector3;

import java.nio.file.Path;

public record PieceAuthoringResult(Path templateFile, IntVector3 origin, BlockBox bounds) {
}
