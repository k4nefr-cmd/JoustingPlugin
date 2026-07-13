package com.jousting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MomentumTrackerTest {

    @AfterEach
    void cleanup() {
        MomentumTracker.clearAll();
    }

    @Test
    void setDistanceStoresValue() {
        UUID id = UUID.randomUUID();
        MomentumTracker.setDistance(id, 5.0);
        assertEquals(5.0, MomentumTracker.getMomentumDistance(id), 1e-9);

        MomentumTracker.setDistance(id, 7.5);
        assertEquals(7.5, MomentumTracker.getMomentumDistance(id), 1e-9);
    }

    @Test
    void setDistanceZeroClearsEntry() {
        UUID id = UUID.randomUUID();
        MomentumTracker.setDistance(id, 5.0);
        MomentumTracker.setDistance(id, 0.0);
        assertEquals(0.0, MomentumTracker.getMomentumDistance(id), 1e-9);
    }

    @Test
    void decayBleedsDownAndClearsAtZero() {
        UUID id = UUID.randomUUID();
        MomentumTracker.setDistance(id, 5.0);
        MomentumTracker.decay(id, 2.0);
        assertEquals(3.0, MomentumTracker.getMomentumDistance(id), 1e-9);

        MomentumTracker.decay(id, 3.0);
        assertEquals(0.0, MomentumTracker.getMomentumDistance(id), 1e-9);
    }

    @Test
    void resetClearsMomentum() {
        UUID id = UUID.randomUUID();
        MomentumTracker.setDistance(id, 10.0);
        assertTrue(MomentumTracker.getMomentumDistance(id) > 0);

        MomentumTracker.resetMomentum(id);
        assertEquals(0.0, MomentumTracker.getMomentumDistance(id), 1e-9);
    }

    @Test
    void fractionUsesTheDamageRamp() {
        UUID id = UUID.randomUUID();
        MomentumTracker.setDistance(id, 10.0);
        // halfway up the 5..15 ramp, not 10/15 of raw distance
        assertEquals(0.5, MomentumTracker.getFraction(id, 5.0, 15.0), 1e-9);
        MomentumTracker.resetMomentum(id);

        MomentumTracker.setDistance(id, 5.0);
        assertEquals(0.0, MomentumTracker.getFraction(id, 5.0, 15.0), 1e-9);
    }

    @Test
    void unknownPlayerIsZero() {
        assertEquals(0.0, MomentumTracker.getMomentumDistance(UUID.randomUUID()), 1e-9);
    }

    // ------------------------------------------------- chargeMomentum math

    @Test
    void straightChargeGrowsByMovement() {
        // riding straight: displacement always equals accumulated distance
        double momentum = 0.0;
        for (int tick = 1; tick <= 20; tick++) {
            momentum = MomentumTracker.chargeMomentum(momentum, 0.5, tick * 0.5, 15.0);
        }
        assertEquals(10.0, momentum, 1e-9);
    }

    @Test
    void chargeIsCappedAtFullDistance() {
        assertEquals(15.0, MomentumTracker.chargeMomentum(14.9, 0.5, 40.0, 15.0), 1e-9);
    }

    @Test
    void circlingIsCappedByDisplacement() {
        // orbiting a target on a 3-block radius: displacement never exceeds the
        // circle's diameter, so momentum can't ramp to full no matter how long
        double radius = 3.0;
        double momentum = 0.0;
        double step = Math.toRadians(10); // ~10 degrees of the circle per tick
        for (int tick = 0; tick < 400; tick++) {
            double a0 = tick * step, a1 = (tick + 1) * step;
            double moved = 2 * radius * Math.sin(step / 2);
            double displacement = radius * Math.hypot(Math.cos(a1) - 1, Math.sin(a1));
            momentum = MomentumTracker.chargeMomentum(momentum, moved, displacement, 15.0);
        }
        assertTrue(momentum <= 2 * radius + 1e-9,
                "circling momentum should be bounded by the circle diameter, was " + momentum);
    }

    @Test
    void ridingBackTowardAnchorShedsMomentum() {
        // 10 blocks out, then 6 blocks back: displacement (4) caps the momentum
        double out = MomentumTracker.chargeMomentum(0.0, 10.0, 10.0, 15.0);
        assertEquals(10.0, out, 1e-9);
        double back = MomentumTracker.chargeMomentum(out, 6.0, 4.0, 15.0);
        assertEquals(4.0, back, 1e-9);
    }

    @Test
    void chargeMomentumNeverNegative() {
        assertEquals(0.0, MomentumTracker.chargeMomentum(0.0, 0.5, -1.0, 15.0), 1e-9);
    }
}
