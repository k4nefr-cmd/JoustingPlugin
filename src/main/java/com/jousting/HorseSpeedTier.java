package com.jousting;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Horse;

/**
 * Classifies a horse into SLOW / MEDIUM / FAST by its movement-speed attribute
 * and provides the momentum-scaled damage math.
 *
 * All damage math is expressed as pure static functions on primitives so it can
 * be unit-tested without a live server.
 */
public final class HorseSpeedTier {

    public enum Tier { SLOW, MEDIUM, FAST }

    private HorseSpeedTier() {}

    /** Reads the horse's movement speed, null-safe. Returns 0 if unavailable. */
    public static double speedOf(Horse horse) {
        if (horse == null) return 0.0;
        AttributeInstance attr = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        return attr == null ? 0.0 : attr.getValue();
    }

    public static Tier tierOf(Horse horse, JoustingConfig config) {
        return tierForSpeed(speedOf(horse), config.getMediumTierSpeedThreshold(),
                config.getHighTierSpeedThreshold());
    }

    /** Pure helper for testing. */
    public static Tier tierForSpeed(double speed, double mediumThreshold, double highThreshold) {
        if (speed >= highThreshold) return Tier.FAST;
        if (speed >= mediumThreshold) return Tier.MEDIUM;
        return Tier.SLOW;
    }

    public static double maxDamageFor(Tier tier, JoustingConfig config) {
        return switch (tier) {
            case FAST -> config.getHighTierMaxDamage();
            case MEDIUM -> config.getMediumTierMaxDamage();
            case SLOW -> config.getLowTierMaxDamage();
        };
    }

    public static double getMaxDamage(Horse horse, JoustingConfig config) {
        return maxDamageFor(tierOf(horse, config), config);
    }

    /**
     * Scales damage with momentum within [0, maxDamage].
     *
     * @param momentum         distance travelled (blocks)
     * @param minDistance      below this, no damage at all
     * @param fullDistance     at/above this, full {@code maxDamage}
     * @param maxDamage        the cap for this horse tier (+ lance bonus)
     * @return damage in [0, maxDamage]
     */
    public static double calculateDamage(double momentum, double minDistance,
                                         double fullDistance, double maxDamage) {
        return maxDamage * momentumFraction(momentum, minDistance, fullDistance);
    }

    /**
     * Momentum fill fraction (0..1) along the damage ramp: 0 at or below {@code minDistance},
     * 1 at or above {@code fullDistance}. The bar, the knockoff chance and the damage all use
     * this, so an empty bar lines up with a zero-damage hit.
     */
    public static double momentumFraction(double momentum, double minDistance, double fullDistance) {
        if (momentum < minDistance) return 0.0;
        double span = Math.max(0.0001, fullDistance - minDistance);
        return Math.max(0.0, Math.min((momentum - minDistance) / span, 1.0));
    }
}
