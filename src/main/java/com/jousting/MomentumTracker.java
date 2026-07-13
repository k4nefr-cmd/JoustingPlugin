package com.jousting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks accumulated travel distance ("momentum") per riding player.
 * Distance only accrues while {@link MomentumTask} decides the horse is moving fast enough.
 */
public final class MomentumTracker {
    private static final Map<UUID, PlayerMomentum> momentumData = new HashMap<>();

    private MomentumTracker() {}

    /** Overwrite the player's momentum with an already-computed value; clears at zero. */
    public static void setDistance(UUID playerUUID, double distance) {
        if (distance <= 0) {
            momentumData.remove(playerUUID);
            return;
        }
        PlayerMomentum data = momentumData.getOrDefault(playerUUID, new PlayerMomentum());
        data.totalDistance = distance;
        momentumData.put(playerUUID, data);
    }

    /**
     * Momentum after one tick of movement. Growth is limited by how far the horse actually
     * moved this tick AND by net displacement from the charge's start point, so riding in
     * circles around a target can never build a full charge — only a straight(ish) run can.
     *
     * @param current      momentum before this tick (blocks)
     * @param moved        distance moved this tick (blocks)
     * @param displacement straight-line distance from the charge anchor (blocks)
     * @param cap          full-momentum distance
     */
    public static double chargeMomentum(double current, double moved, double displacement, double cap) {
        return Math.max(0.0, Math.min(Math.min(current + moved, displacement), cap));
    }

    /** Bleed momentum down by {@code amount} (blocks); clears the entry at zero. */
    public static void decay(UUID playerUUID, double amount) {
        PlayerMomentum data = momentumData.get(playerUUID);
        if (data == null) return;
        data.totalDistance -= amount;
        if (data.totalDistance <= 0) momentumData.remove(playerUUID);
        else momentumData.put(playerUUID, data);
    }

    public static double getMomentumDistance(UUID playerUUID) {
        PlayerMomentum data = momentumData.get(playerUUID);
        return data == null ? 0.0 : data.totalDistance;
    }

    /** Fill fraction in [0,1] for UI, along the same ramp the damage curve uses. */
    public static double getFraction(UUID playerUUID, double minDistance, double fullDistance) {
        return HorseSpeedTier.momentumFraction(getMomentumDistance(playerUUID), minDistance, fullDistance);
    }

    public static void resetMomentum(UUID playerUUID) {
        momentumData.remove(playerUUID);
    }

    public static void clearAll() {
        momentumData.clear();
    }

    private static class PlayerMomentum {
        double totalDistance = 0;
    }
}
