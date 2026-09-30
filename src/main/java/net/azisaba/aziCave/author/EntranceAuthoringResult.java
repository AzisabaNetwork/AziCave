package net.azisaba.aziCave.author;

import net.azisaba.aziCave.math.BlockBox;
import net.azisaba.aziCave.math.Direction;
import net.azisaba.aziCave.math.IntVector3;

import java.nio.file.Path;

public record EntranceAuthoringResult(Path templateFile, IntVector3 origin, BlockBox plane, Direction facing) {
}
