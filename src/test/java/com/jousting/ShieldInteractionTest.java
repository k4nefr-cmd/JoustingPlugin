package com.jousting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure math behind shield blocking: frontal arc and Unbreaking-aware durability loss. */
class ShieldInteractionTest {

    // Bukkit yaw: 0 = facing +Z (south), 90 = facing -X (west).

    @Test
    void attackerInFrontIsBlocked() {
        // victim faces south (+Z), attacker one block further south
        assertTrue(DamageCalculator.isFrontalHit(0.0, 0.0, 1.0));
    }

    @Test
    void attackerBehindIsNotBlocked() {
        // victim faces south (+Z), attacker to the north (-Z)
        assertFalse(DamageCalculator.isFrontalHit(0.0, 0.0, -1.0));
    }

    @Test
    void frontalArcFollowsFacing() {
        // victim faces west (-X): attacker at -X is frontal, +X is behind
        assertTrue(DamageCalculator.isFrontalHit(90.0, -1.0, 0.0));
        assertFalse(DamageCalculator.isFrontalHit(90.0, 1.0, 0.0));
    }

    @Test
    void exactlyPerpendicularIsNotBlocked() {
        // victim faces south, attacker due east: on the arc boundary, goes through
        assertFalse(DamageCalculator.isFrontalHit(0.0, 1.0, 0.0));
    }

    @Test
    void diagonalFrontalHitIsBlocked() {
        // victim faces south, attacker south-east: still inside the 180-degree arc
        assertTrue(DamageCalculator.isFrontalHit(0.0, 1.0, 1.0));
    }

    // ------------------------------------------------- durability loss

    @Test
    void noUnbreakingTakesFullDamage() {
        assertEquals(50, DamageCalculator.shieldDurabilityLoss(50, 0));
    }

    @Test
    void unbreakingScalesDamageDown() {
        assertEquals(25, DamageCalculator.shieldDurabilityLoss(50, 1));
        assertEquals(17, DamageCalculator.shieldDurabilityLoss(50, 2));
        assertEquals(13, DamageCalculator.shieldDurabilityLoss(50, 3));
    }

    @Test
    void lossNeverDropsBelowOneWhileConfigured() {
        assertEquals(1, DamageCalculator.shieldDurabilityLoss(1, 3));
    }

    @Test
    void zeroConfiguredDamageMeansNoLoss() {
        assertEquals(0, DamageCalculator.shieldDurabilityLoss(0, 0));
        assertEquals(0, DamageCalculator.shieldDurabilityLoss(0, 3));
    }

    @Test
    void negativeUnbreakingTreatedAsZero() {
        assertEquals(50, DamageCalculator.shieldDurabilityLoss(50, -1));
    }
}
