package com.jousting;

/**
 * Pure damage-balancing math (no Bukkit types) so it can be unit-tested.
 * All values here are hearts; the listener converts to Minecraft damage points
 * (2 points = 1 heart) only when it applies the event damage.
 *
 * Lance damage is intentionally independent of Sharpness and Strength: a charge's
 * damage comes only from momentum, horse speed tier and lance tier, then is clamped
 * to a hard cap so a single hit can never one-shot a full-health (10-heart) player.
 */
public final class DamageCalculator {

    private DamageCalculator() {}

    /** Clamp the momentum-scaled base damage to [0, hardCap]. */
    public static double clamp(double base, double hardCap) {
        if (base < 0) return 0.0;
        return Math.min(base, hardCap);
    }

    /**
     * Whether an attack from the given relative position lands in the victim's frontal
     * 180-degree arc — a raised shield only blocks these, matching vanilla.
     *
     * @param victimYaw Bukkit yaw of the victim in degrees (0 = +Z/south, 90 = -X/west)
     * @param dx        attacker X minus victim X
     * @param dz        attacker Z minus victim Z
     */
    public static boolean isFrontalHit(double victimYaw, double dx, double dz) {
        double yawRad = Math.toRadians(victimYaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        return fx * dx + fz * dz > 0;
    }

    /**
     * Durability points actually removed from a shield, honouring Unbreaking:
     * vanilla gives each point a 1/(level+1) chance to apply, so we scale the
     * batch deterministically. Never less than 1 while any damage is configured.
     */
    public static int shieldDurabilityLoss(int configuredDamage, int unbreakingLevel) {
        if (configuredDamage <= 0) return 0;
        int level = Math.max(0, unbreakingLevel);
        return Math.max(1, (int) Math.round(configuredDamage / (level + 1.0)));
    }
}
