package com.jousting;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CooldownManagerTest {

    /** Mutable fake clock. */
    static class Clock {
        long now = 0L;
    }

    @Test
    void onCooldownUntilDurationElapses() {
        Clock clock = new Clock();
        CooldownManager cd = new CooldownManager(() -> clock.now);
        UUID id = UUID.randomUUID();

        cd.start(id, 1500);
        assertTrue(cd.isOnCooldown(id));

        clock.now = 1499;
        assertTrue(cd.isOnCooldown(id));

        clock.now = 1500;
        assertFalse(cd.isOnCooldown(id), "cooldown ends exactly at expiry");
    }

    @Test
    void noCooldownByDefault() {
        CooldownManager cd = new CooldownManager(() -> 0L);
        assertFalse(cd.isOnCooldown(UUID.randomUUID()));
    }

    @Test
    void zeroDurationExpiresImmediately() {
        Clock clock = new Clock();
        CooldownManager cd = new CooldownManager(() -> clock.now);
        UUID id = UUID.randomUUID();

        cd.start(id, 0);
        assertFalse(cd.isOnCooldown(id));
    }

    @Test
    void playersHaveIndependentCooldowns() {
        Clock clock = new Clock();
        CooldownManager cd = new CooldownManager(() -> clock.now);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        cd.start(a, 1000);
        cd.start(b, 2000);

        clock.now = 1500;
        assertFalse(cd.isOnCooldown(a), "a expired at 1000");
        assertTrue(cd.isOnCooldown(b), "b still cooling until 2000");

        cd.clear(b);
        assertFalse(cd.isOnCooldown(b));
    }

    @Test
    void clearRemovesCooldown() {
        Clock clock = new Clock();
        CooldownManager cd = new CooldownManager(() -> clock.now);
        UUID id = UUID.randomUUID();
        cd.start(id, 1000);
        cd.clear(id);
        assertFalse(cd.isOnCooldown(id));
    }
}
