package com.jousting;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanceTierTest {

    @Test
    void mapsDistinctMaterials() {
        assertEquals(LanceTier.IRON, LanceTier.fromMaterial(Material.IRON_SPEAR));
        assertEquals(LanceTier.GOLD, LanceTier.fromMaterial(Material.GOLDEN_SPEAR));
        assertEquals(LanceTier.DIAMOND, LanceTier.fromMaterial(Material.DIAMOND_SPEAR));
    }

    @Test
    void nonLanceMaterialIsNull() {
        assertNull(LanceTier.fromMaterial(Material.STONE));
        assertNull(LanceTier.fromMaterial(Material.END_ROD));
        assertNull(LanceTier.fromMaterial(null));
    }

    @Test
    void fromNameCaseInsensitive() {
        assertEquals(LanceTier.DIAMOND, LanceTier.fromName("diamond"));
        assertEquals(LanceTier.IRON, LanceTier.fromName("IRON"));
        assertNull(LanceTier.fromName("wood"));
        assertNull(LanceTier.fromName(null));
    }
}
