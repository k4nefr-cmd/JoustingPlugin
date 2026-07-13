package com.jousting;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed view over config.yml. Reloaded via {@link #reload()}.
 * Final because the constructor calls {@link #reload()}.
 */
public final class JoustingConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Lance tiers: material -> (damage bonus, max uses)
    private Map<Material, Double> lanceDamageBonus;
    private Map<Material, Integer> lanceMaxUsesByMaterial;
    private int lanceMaxUses;
    private long lanceCooldownMs;

    // Horse speed tiers
    private double mediumTierSpeedThreshold;
    private double highTierSpeedThreshold;
    private double lowTierMaxDamage;
    private double mediumTierMaxDamage;
    private double highTierMaxDamage;

    // Momentum
    private double minimumRunSpeed;
    private double momentumGainMultiplier;
    private double minimumMomentumDistance;
    private double fullMomentumDistance;
    private double momentumDecayPerTick;
    private boolean debugDamage;

    // Balancing
    private double finalDamageHardCap;

    // Shield
    private int shieldDurabilityDamage;
    private boolean shieldCooldownOnBlock;
    private boolean shieldKnockbackOnBlock;

    // Knockoff / knockback
    private int knockoffChanceZeroMomentum;
    private int knockoffChanceFullMomentum;
    private double knockbackStrength;

    // Sounds (parsed once per reload so hits don't re-parse enum names)
    private Sound hitSound;
    private Sound knockoffSound;
    private Sound shieldSound;
    private Sound breakSound;

    public JoustingConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Lance tiers
        this.lanceDamageBonus = new HashMap<>();
        this.lanceMaxUsesByMaterial = new HashMap<>();
        this.lanceMaxUses = config.getInt("lance-max-uses", 10);
        ConfigurationSection tierSection = config.getConfigurationSection("lance-tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("Invalid lance tier material: " + key + ", skipping");
                    continue;
                }
                lanceDamageBonus.put(mat, tierSection.getDouble(key + ".damage-bonus", 0.0));
                lanceMaxUsesByMaterial.put(mat, tierSection.getInt(key + ".max-uses", lanceMaxUses));
            }
        }
        if (lanceDamageBonus.isEmpty()) {
            plugin.getLogger().warning("No valid lance tiers configured; falling back to END_ROD");
            lanceDamageBonus.put(Material.END_ROD, 0.0);
            lanceMaxUsesByMaterial.put(Material.END_ROD, lanceMaxUses);
        }

        this.lanceCooldownMs = config.getLong("lance-cooldown-ms", 1500L);

        // Speed tiers
        this.mediumTierSpeedThreshold = config.getDouble("medium-tier-speed-threshold", 0.2);
        this.highTierSpeedThreshold = config.getDouble("high-tier-speed-threshold", 0.28);
        this.lowTierMaxDamage = config.getDouble("low-tier-max-damage", 3.0);
        this.mediumTierMaxDamage = config.getDouble("medium-tier-max-damage", 5.0);
        this.highTierMaxDamage = config.getDouble("high-tier-max-damage", 6.0);

        // Momentum
        this.minimumRunSpeed = config.getDouble("minimum-run-speed", 0.15);
        this.momentumGainMultiplier = config.getDouble("momentum-gain-multiplier", 0.5);
        this.minimumMomentumDistance = config.getDouble("minimum-momentum-distance", 5.0);
        this.fullMomentumDistance = config.getDouble("full-momentum-distance", 15.0);
        this.momentumDecayPerTick = config.getDouble("momentum-decay-per-tick", 0.5);
        this.debugDamage = config.getBoolean("debug-damage", false);

        // Balancing
        this.finalDamageHardCap = config.getDouble("final-damage-hard-cap", 9.0);

        // Shield
        this.shieldDurabilityDamage = config.getInt("shield.durability-damage", 50);
        this.shieldCooldownOnBlock = config.getBoolean("shield.cooldown-on-block", true);
        this.shieldKnockbackOnBlock = config.getBoolean("shield.knockback-on-block", true);

        // Knockoff / knockback
        this.knockoffChanceZeroMomentum = config.getInt("knockoff-chance-zero-momentum", 5);
        this.knockoffChanceFullMomentum = config.getInt("knockoff-chance-full-momentum", 20);
        this.knockbackStrength = config.getDouble("knockback-strength", 0.5);

        // Sounds
        this.hitSound = parseSound(config.getString("sounds.hit", "ENTITY_PLAYER_ATTACK_STRONG"));
        this.knockoffSound = parseSound(config.getString("sounds.knockoff", "ENTITY_ITEM_BREAK"));
        this.shieldSound = parseSound(config.getString("sounds.shield", "ITEM_SHIELD_BLOCK"));
        this.breakSound = parseSound(config.getString("sounds.break", "ENTITY_ITEM_BREAK"));

        validate();
    }

    private Sound parseSound(String key) {
        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound key: " + key + "; sound disabled");
            return null;
        }
    }

    /** Sanity-checks loaded values; clamps where safe and warns otherwise. */
    private void validate() {
        // If medium >= high the MEDIUM tier becomes unreachable, so nudge high above medium.
        if (mediumTierSpeedThreshold >= highTierSpeedThreshold) {
            double corrected = mediumTierSpeedThreshold + 0.01;
            plugin.getLogger().warning("medium-tier-speed-threshold (" + mediumTierSpeedThreshold
                    + ") must be less than high-tier-speed-threshold (" + highTierSpeedThreshold
                    + "); using " + corrected);
            highTierSpeedThreshold = corrected;
        }

        // max-uses of 0 would break every lance on the first swing.
        lanceMaxUses = atLeastOne("lance-max-uses", lanceMaxUses);
        lanceMaxUsesByMaterial.replaceAll(
                (mat, uses) -> atLeastOne("lance-tiers." + mat + ".max-uses", uses));

        // negative strength would pull the target toward the rider instead of away
        knockbackStrength = nonNegative("knockback-strength", knockbackStrength);

        knockoffChanceZeroMomentum = clampPercent("knockoff-chance-zero-momentum", knockoffChanceZeroMomentum);
        knockoffChanceFullMomentum = clampPercent("knockoff-chance-full-momentum", knockoffChanceFullMomentum);

        lowTierMaxDamage = nonNegative("low-tier-max-damage", lowTierMaxDamage);
        mediumTierMaxDamage = nonNegative("medium-tier-max-damage", mediumTierMaxDamage);
        highTierMaxDamage = nonNegative("high-tier-max-damage", highTierMaxDamage);
        finalDamageHardCap = nonNegative("final-damage-hard-cap", finalDamageHardCap);

        // negatives here would build momentum while standing still, or never let it bleed off
        minimumRunSpeed = nonNegative("minimum-run-speed", minimumRunSpeed);
        momentumGainMultiplier = nonNegative("momentum-gain-multiplier", momentumGainMultiplier);
        momentumDecayPerTick = nonNegative("momentum-decay-per-tick", momentumDecayPerTick);

        minimumMomentumDistance = nonNegative("minimum-momentum-distance", minimumMomentumDistance);
        // full must sit above minimum, or the momentum cap never reaches the damage threshold.
        if (fullMomentumDistance <= minimumMomentumDistance) {
            double corrected = minimumMomentumDistance + 1.0;
            plugin.getLogger().warning("full-momentum-distance (" + fullMomentumDistance
                    + ") must be greater than minimum-momentum-distance (" + minimumMomentumDistance
                    + "); using " + corrected);
            fullMomentumDistance = corrected;
        }

        // Negative durability damage would repair the shield on every block.
        if (shieldDurabilityDamage < 0) {
            plugin.getLogger().warning("shield.durability-damage (" + shieldDurabilityDamage
                    + ") must be >= 0; using 0");
            shieldDurabilityDamage = 0;
        }
    }

    private int clampPercent(String path, int value) {
        if (value < 0 || value > 100) {
            plugin.getLogger().warning(path + " (" + value + ") clamped to 0-100");
            return Math.max(0, Math.min(100, value));
        }
        return value;
    }

    private int atLeastOne(String path, int value) {
        if (value < 1) {
            plugin.getLogger().warning(path + " (" + value + ") must be >= 1; using 1");
            return 1;
        }
        return value;
    }

    private double nonNegative(String path, double value) {
        if (value < 0) {
            plugin.getLogger().warning(path + " (" + value + ") must be >= 0; using 0");
            return 0.0;
        }
        return value;
    }

    public boolean isLanceItem(Material material) { return lanceDamageBonus.containsKey(material); }
    public double getLanceDamageBonus(Material material) { return lanceDamageBonus.getOrDefault(material, 0.0); }
    public int getLanceMaxUses(Material material) { return lanceMaxUsesByMaterial.getOrDefault(material, lanceMaxUses); }
    public int getLanceMaxUses() { return lanceMaxUses; }
    public long getLanceCooldownMs() { return lanceCooldownMs; }

    public double getMediumTierSpeedThreshold() { return mediumTierSpeedThreshold; }
    public double getHighTierSpeedThreshold() { return highTierSpeedThreshold; }
    public double getLowTierMaxDamage() { return lowTierMaxDamage; }
    public double getMediumTierMaxDamage() { return mediumTierMaxDamage; }
    public double getHighTierMaxDamage() { return highTierMaxDamage; }

    public double getMinimumRunSpeed() { return minimumRunSpeed; }
    public double getMomentumGainMultiplier() { return momentumGainMultiplier; }
    public double getMinimumMomentumDistance() { return minimumMomentumDistance; }
    public double getFullMomentumDistance() { return fullMomentumDistance; }
    public double getMomentumDecayPerTick() { return momentumDecayPerTick; }
    public boolean isDebugDamage() { return debugDamage; }

    public double getFinalDamageHardCap() { return finalDamageHardCap; }

    public int getShieldDurabilityDamage() { return shieldDurabilityDamage; }
    public boolean isShieldCooldownOnBlock() { return shieldCooldownOnBlock; }
    public boolean isShieldKnockbackOnBlock() { return shieldKnockbackOnBlock; }

    public int getKnockoffChanceZeroMomentum() { return knockoffChanceZeroMomentum; }
    public int getKnockoffChanceFullMomentum() { return knockoffChanceFullMomentum; }
    public double getKnockbackStrength() { return knockbackStrength; }

    public Sound getHitSound() { return hitSound; }
    public Sound getKnockoffSound() { return knockoffSound; }
    public Sound getShieldSound() { return shieldSound; }
    public Sound getBreakSound() { return breakSound; }
}
