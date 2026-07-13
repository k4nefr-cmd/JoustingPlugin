package com.jousting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorseSpeedTierTest {

    private static final double MED = 0.2;
    private static final double HIGH = 0.28;

    // A degenerate full <= min config pins damage at zero (validate() coerces it in practice).
    @Test
    void zeroDamageWhenFullBelowMin() {
        assertEquals(0.0, HorseSpeedTier.calculateDamage(3.0, 5.0, 3.0, 8.0), 1e-9);
        assertEquals(8.0, HorseSpeedTier.calculateDamage(6.0, 5.0, 6.0, 8.0), 1e-9);
    }

    @Test
    void classifiesThreeTiers() {
        assertEquals(HorseSpeedTier.Tier.SLOW, HorseSpeedTier.tierForSpeed(0.10, MED, HIGH));
        assertEquals(HorseSpeedTier.Tier.MEDIUM, HorseSpeedTier.tierForSpeed(0.24, MED, HIGH));
        assertEquals(HorseSpeedTier.Tier.FAST, HorseSpeedTier.tierForSpeed(0.30, MED, HIGH));
    }

    @Test
    void tierBoundariesAreInclusiveOnUpperTier() {
        assertEquals(HorseSpeedTier.Tier.MEDIUM, HorseSpeedTier.tierForSpeed(MED, MED, HIGH));
        assertEquals(HorseSpeedTier.Tier.FAST, HorseSpeedTier.tierForSpeed(HIGH, MED, HIGH));
    }

    @Test
    void noDamageBelowMinimumMomentum() {
        assertEquals(0.0, HorseSpeedTier.calculateDamage(3.0, 5.0, 15.0, 6.0), 1e-9);
    }

    @Test
    void zeroDamageAtExactlyMinimumDistance() {
        // The ramp starts at the minimum: fraction 0 -> damage 0.
        assertEquals(0.0, HorseSpeedTier.calculateDamage(5.0, 5.0, 15.0, 6.0), 1e-9);
    }

    @Test
    void fullDamageAtOrAboveFullDistance() {
        assertEquals(6.0, HorseSpeedTier.calculateDamage(15.0, 5.0, 15.0, 6.0), 1e-9);
        assertEquals(6.0, HorseSpeedTier.calculateDamage(40.0, 5.0, 15.0, 6.0), 1e-9);
    }

    @Test
    void scalesLinearlyBetween() {
        // halfway between min(5) and full(15) -> 10 -> half of cap
        assertEquals(3.0, HorseSpeedTier.calculateDamage(10.0, 5.0, 15.0, 6.0), 1e-9);
    }

    @Test
    void neverExceedsCap() {
        double d = HorseSpeedTier.calculateDamage(1000.0, 5.0, 15.0, 6.0);
        assertTrue(d <= 6.0);
    }

    @Test
    void momentumFractionClamped() {
        assertEquals(0.0, HorseSpeedTier.momentumFraction(0, 5.0, 15.0), 1e-9);
        assertEquals(0.0, HorseSpeedTier.momentumFraction(5.0, 5.0, 15.0), 1e-9);
        assertEquals(0.5, HorseSpeedTier.momentumFraction(10.0, 5.0, 15.0), 1e-9);
        assertEquals(1.0, HorseSpeedTier.momentumFraction(100, 5.0, 15.0), 1e-9);
    }

    @Test
    void fractionRampsFromZeroAtMinToOneAtFull() {
        // 5..15, so 7.5 is a quarter of the way up and 12.5 three quarters.
        assertEquals(0.0, HorseSpeedTier.momentumFraction(5.0, 5.0, 15.0), 1e-9);
        assertEquals(0.25, HorseSpeedTier.momentumFraction(7.5, 5.0, 15.0), 1e-9);
        assertEquals(0.75, HorseSpeedTier.momentumFraction(12.5, 5.0, 15.0), 1e-9);
        assertEquals(1.0, HorseSpeedTier.momentumFraction(15.0, 5.0, 15.0), 1e-9);
    }
}
