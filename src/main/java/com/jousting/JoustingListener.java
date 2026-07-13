package com.jousting;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class JoustingListener implements Listener {
    private final JoustingPlugin plugin;
    private final JoustingConfig config;
    private final MomentumBarManager barManager;
    private final CooldownManager cooldowns;
    private final LanceItems lances;
    private final Random random = new Random();

    // Vanilla spears dismount riders on their own; we suppress that so the momentum-based
    // knockoff roll is the only thing that can unseat a jousting target. Victims of a lance
    // hit are marked here briefly, and dismounts we approved are let through.
    private static final long DISMOUNT_SUPPRESS_WINDOW_MS = 100;
    private final Map<UUID, Long> suppressVanillaDismount = new HashMap<>();
    private final Set<UUID> approvedKnockoffs = new HashSet<>();

    public JoustingListener(JoustingPlugin plugin, JoustingConfig config,
                            MomentumBarManager barManager, CooldownManager cooldowns,
                            LanceItems lances) {
        this.plugin = plugin;
        this.config = config;
        this.barManager = barManager;
        this.cooldowns = cooldowns;
        this.lances = lances;
    }

    // Momentum tracking and the bar itself live in MomentumTask (per-tick poll).

    // HIGHEST so region/PvP protection plugins (WorldGuard etc.) cancel first; otherwise we'd
    // burn a lance use and start the cooldown for a hit they then deny.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 1.21.11 spears hit with their own SPEAR damage type (the mounted charge), not
        // plain ENTITY_ATTACK — gating on cause alone silently skipped every real joust hit.
        // Accept both spear and ordinary melee, and reject sweeps/thorns/indirect damage.
        DamageType type = event.getDamageSource().getDamageType();
        if (type != DamageType.SPEAR && type != DamageType.PLAYER_ATTACK) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getDamager() instanceof Player damager)) return;

        ItemStack mainHand = damager.getInventory().getItemInMainHand();
        if (!config.isLanceItem(mainHand.getType())) return;

        // A spent lance is decorative: it deals no damage at all, mounted or not.
        // Checked before the mount/permission gates so it can't fall through to vanilla melee.
        int maxUses = config.getLanceMaxUses(mainHand.getType());
        int uses = lances.getUses(mainHand);
        if (uses >= maxUses) {
            event.setCancelled(true);
            return;
        }

        if (!(damager.getVehicle() instanceof Horse horse)) return;
        // Without jousting.use (default true) a lance is just an ordinary melee weapon.
        if (!damager.hasPermission("jousting.use")) return;

        // Armour stands are LivingEntity too but aren't jousting targets; leave them to vanilla.
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof ArmorStand) return;

        // Every lance hit on a mounted target suppresses the spear's built-in dismount,
        // including hits that fall through to vanilla melee below (cooldown, no momentum) —
        // otherwise vanilla unseats the rider on every tap regardless of our knockoff roll.
        if (victim.getVehicle() instanceof Horse) {
            suppressVanillaDismount.put(victim.getUniqueId(), System.currentTimeMillis());
        }

        // The early returns below don't cancel: the ordinary melee swing still lands, only the
        // lance charge is withheld.
        if (cooldowns.isOnCooldown(damager.getUniqueId())) return;

        LanceTier tier = LanceTier.fromMaterial(mainHand.getType());

        // --- damage calculation -------------------------------------------------
        double momentum = MomentumTracker.getMomentumDistance(damager.getUniqueId());
        double speedCap = HorseSpeedTier.getMaxDamage(horse, config);
        double base = HorseSpeedTier.calculateDamage(
                momentum,
                config.getMinimumMomentumDistance(),
                config.getFullMomentumDistance(),
                speedCap + config.getLanceDamageBonus(mainHand.getType()));

        if (base <= 0) return; // not enough momentum for a charge; ordinary swing still lands

        // Sharpness/Strength are ignored on purpose so buffs can't inflate a charge.
        double damage = DamageCalculator.clamp(base, config.getFinalDamageHardCap());

        // A raised shield only blocks hits from the victim's frontal arc, like vanilla:
        // a lance in the back goes through.
        boolean blocked = victim instanceof Player vp && vp.isBlocking()
                && DamageCalculator.isFrontalHit(vp.getLocation().getYaw(),
                        damager.getLocation().getX() - vp.getLocation().getX(),
                        damager.getLocation().getZ() - vp.getLocation().getZ());
        if (blocked) {
            event.setCancelled(true); // shield absorbs; no health damage
            Player target = (Player) victim;
            EquipmentSlot hand = shieldHand(target);
            boolean shieldBroke = hand != null && damageShield(target, hand);
            if (config.isShieldKnockbackOnBlock()) {
                applyKnockback(damager, target);
            }
            if (!shieldBroke) playSound(target, config.getShieldSound());
            consumeUse(mainHand, uses, tier, maxUses);
            if (config.isShieldCooldownOnBlock()) {
                cooldowns.start(damager.getUniqueId(), config.getLanceCooldownMs());
            }
            MomentumTracker.resetMomentum(damager.getUniqueId());
            barManager.update(damager, 0.0, tier);
            return; // blocked hit deals no health damage
        }

        // Set the damage on this event rather than calling victim.damage(), which would re-fire
        // EntityDamageByEntityEvent and recurse. Config is in hearts; Minecraft uses half-hearts.
        event.setDamage(damage * 2.0);
        playSound(victim, config.getHitSound());
        applyKnockback(damager, victim);

        if (config.isDebugDamage()) {
            double frac = HorseSpeedTier.momentumFraction(momentum,
                    config.getMinimumMomentumDistance(), config.getFullMomentumDistance());
            damager.sendMessage("§e[Joust] §f" + String.format("%.1f", damage) + " hearts §7| momentum "
                    + (int) Math.round(frac * 100) + "% | horse " + HorseSpeedTier.tierOf(horse, config)
                    + " | lance " + (tier == null ? "?" : tier.name()));
        }

        // JOUSTING mode (mounted vs mounted) only: chance to dismount.
        boolean joustMode = victim instanceof Player tp && tp.getVehicle() instanceof Horse;
        if (joustMode && shouldKnockoff(momentum)) {
            approvedKnockoffs.add(victim.getUniqueId());
            victim.leaveVehicle();
            playSound(victim, config.getKnockoffSound());
        }

        consumeUse(mainHand, uses, tier, maxUses);
        cooldowns.start(damager.getUniqueId(), config.getLanceCooldownMs());
        MomentumTracker.resetMomentum(damager.getUniqueId());
        barManager.update(damager, 0.0, tier);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (approvedKnockoffs.remove(id)) return; // our knockoff roll: let it through
        Long hitAt = suppressVanillaDismount.remove(id);
        if (hitAt != null && System.currentTimeMillis() - hitAt <= DISMOUNT_SUPPRESS_WINDOW_MS) {
            event.setCancelled(true); // vanilla spear dismount: the roll said no
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            MomentumTracker.resetMomentum(player.getUniqueId());
            barManager.remove(player.getUniqueId());
            plugin.getMomentumTask().forget(player.getUniqueId());
            // cooldown is left to expire, so remounting can't reset it
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MomentumTracker.resetMomentum(player.getUniqueId());
        barManager.remove(player.getUniqueId());
        cooldowns.clear(player.getUniqueId());
        plugin.getMomentumTask().forget(player.getUniqueId());
        suppressVanillaDismount.remove(player.getUniqueId());
        approvedKnockoffs.remove(player.getUniqueId());
    }

    // ---------------------------------------------------------------- helpers

    private void consumeUse(ItemStack lance, int currentUses, LanceTier tier, int maxUses) {
        // LanceItems.setUses refreshes durability lore and renames to "Broken Lance" at the cap.
        lances.setUses(lance, currentUses + 1, tier, maxUses);
    }

    private EquipmentSlot shieldHand(Player target) {
        if (target.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
            return EquipmentSlot.OFF_HAND;
        }
        if (target.getInventory().getItemInMainHand().getType() == Material.SHIELD) {
            return EquipmentSlot.HAND;
        }
        return null;
    }

    /** Damages the target's shield; returns true if it broke (and plays the break sound). */
    private boolean damageShield(Player target, EquipmentSlot hand) {
        ItemStack shield = hand == EquipmentSlot.OFF_HAND
                ? target.getInventory().getItemInOffHand()
                : target.getInventory().getItemInMainHand();
        if (shield == null) return false;
        ItemMeta meta = shield.getItemMeta();
        if (!(meta instanceof Damageable dmg)) return false;
        if (meta.isUnbreakable()) return false; // still blocks, but never wears or breaks

        int loss = DamageCalculator.shieldDurabilityLoss(
                config.getShieldDurabilityDamage(),
                shield.getEnchantmentLevel(Enchantment.UNBREAKING));
        if (loss <= 0) return false;

        int max = shield.getType().getMaxDurability();
        int newDamage = dmg.getDamage() + loss;
        boolean broke = max > 0 && newDamage >= max;
        if (broke) {
            shield.setAmount(0);
            playSound(target, config.getBreakSound());
        } else {
            dmg.setDamage(newDamage);
            shield.setItemMeta(meta);
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            target.getInventory().setItemInOffHand(shield);
        } else {
            target.getInventory().setItemInMainHand(shield);
        }
        return broke;
    }

    private void applyKnockback(Player source, LivingEntity victim) {
        Vector dir = source.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() == 0) dir = new Vector(0, 0, 1);
        dir.normalize().multiply(config.getKnockbackStrength()).setY(0.25);

        // A passenger's position is driven by its vehicle, so velocity applied to a mounted
        // player is discarded. Push whatever is actually carrying them.
        Entity target = victim.getVehicle() != null ? victim.getVehicle() : victim;
        target.setVelocity(target.getVelocity().add(dir));
    }

    private boolean shouldKnockoff(double momentum) {
        double normalized = HorseSpeedTier.momentumFraction(momentum,
                config.getMinimumMomentumDistance(), config.getFullMomentumDistance());
        double zero = config.getKnockoffChanceZeroMomentum();
        double full = config.getKnockoffChanceFullMomentum();
        int chance = (int) Math.round(zero + (full - zero) * normalized);
        return random.nextInt(100) < chance;
    }

    // Sounds play where the impact happened (the victim), so bystanders hear the joust.
    private void playSound(LivingEntity at, Sound sound) {
        if (sound == null) return; // invalid key already warned about at config load
        at.getWorld().playSound(at.getLocation(), sound, 1.0f, 1.0f);
    }
}
