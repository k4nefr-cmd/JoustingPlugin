package com.jousting;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows each riding player a momentum meter as a BossBar. Colour shifts
 * RED -> YELLOW -> GREEN as momentum fills.
 */
public class MomentumBarManager {
    private final Map<UUID, BossBar> bars = new HashMap<>();

    /** Create/update the player's bar to the given fill fraction (0..1). */
    public void update(Player player, double fraction, LanceTier tier) {
        if (!player.isOnline()) return;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(titleFor(tier, fraction), colorFor(fraction), BarStyle.SEGMENTED_10);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        } else {
            bar.setTitle(titleFor(tier, fraction));
            bar.setColor(colorFor(fraction));
        }
        bar.setProgress(fraction);
        bar.setVisible(true);
    }

    public void remove(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    public void clearAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        bars.clear();
    }

    private String titleFor(LanceTier tier, double fraction) {
        String label = tier == null ? "Momentum" : tier.getDisplayName() + " Momentum";
        return label + " — " + (int) Math.round(fraction * 100) + "%";
    }

    private BarColor colorFor(double fraction) {
        if (fraction >= 0.99) return BarColor.GREEN;
        if (fraction >= 0.5) return BarColor.YELLOW;
        return BarColor.RED;
    }
}
