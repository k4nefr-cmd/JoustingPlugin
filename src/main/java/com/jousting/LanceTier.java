package com.jousting;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * The three lance tiers, each backed by a distinct base material.
 *
 * Materials are resolved by name via {@link Material#matchMaterial(String)} at class load,
 * so if a server version lacks one (e.g. a build without the spear items) that tier simply
 * resolves to null and is skipped — it never breaks compilation.
 *
 * Backed by the in-game craftable spears (Mounts of Mayhem update).
 */
public enum LanceTier {
    IRON("IRON_SPEAR", "Iron Lance", NamedTextColor.WHITE),
    GOLD("GOLDEN_SPEAR", "Gold Lance", NamedTextColor.GOLD),
    DIAMOND("DIAMOND_SPEAR", "Diamond Lance", NamedTextColor.AQUA);

    private final Material material;
    private final String displayName;
    private final NamedTextColor color;

    LanceTier(String materialName, String displayName, NamedTextColor color) {
        this.material = Material.matchMaterial(materialName);
        this.displayName = displayName;
        this.color = color;
    }

    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public NamedTextColor getColor() { return color; }

    /** @return the tier whose base material matches, or null if none. */
    public static LanceTier fromMaterial(Material material) {
        if (material == null) return null;
        for (LanceTier tier : values()) {
            if (tier.material != null && tier.material == material) return tier;
        }
        return null;
    }

    public static LanceTier fromName(String name) {
        if (name == null) return null;
        for (LanceTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) return tier;
        }
        return null;
    }
}
