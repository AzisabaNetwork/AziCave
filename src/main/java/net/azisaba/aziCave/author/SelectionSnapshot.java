package net.azisaba.aziCave.author;

import net.azisaba.aziCave.math.IntVector3;

public record SelectionSnapshot(String worldName, IntVector3 min, IntVector3 max) {
}
