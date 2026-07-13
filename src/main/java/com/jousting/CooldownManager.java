package com.jousting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Tracks per-player lance cooldowns. Uses an injectable clock so the logic
 * can be unit-tested without sleeping.
 */
public class CooldownManager {
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final LongSupplier clock;

    public CooldownManager() {
        this(System::currentTimeMillis);
    }

    public CooldownManager(LongSupplier clock) {
        this.clock = clock;
    }

    /** Put the player on cooldown for {@code durationMs} from now. */
    public void start(UUID uuid, long durationMs) {
        cooldownUntil.put(uuid, clock.getAsLong() + durationMs);
    }

    public boolean isOnCooldown(UUID uuid) {
        Long until = cooldownUntil.get(uuid);
        if (until == null) return false;
        if (clock.getAsLong() >= until) {
            cooldownUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public void clear(UUID uuid) {
        cooldownUntil.remove(uuid);
    }

    public void clearAll() {
        cooldownUntil.clear();
    }
}
