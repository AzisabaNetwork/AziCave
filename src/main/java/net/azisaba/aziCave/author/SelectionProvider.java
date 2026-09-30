package net.azisaba.aziCave.author;

import org.bukkit.entity.Player;

public interface SelectionProvider {
    boolean isAvailable();

    String describeAvailability();

    SelectionSnapshot getSelection(Player player) throws SelectionLookupException;
}
