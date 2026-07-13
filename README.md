# JoustingPlugin

A Minecraft Paper 1.21.11 plugin that adds a complete jousting system with lances, momentum, and knockoff mechanics.

## Features

✨ **Lance System** - Players on horses holding a lance (the craftable spears) deal jousting damage

⚡ **Momentum Tracking** - Distance traveled builds momentum, more distance = more damage. Momentum is bounded by straight-line displacement from where the charge began, so circling a target never builds a full charge — you need a real run-up.

🐴 **Speed Tiers** - Horses are classified by their movement-speed attribute (thresholds configurable)
  - Slow tier: up to 3 hearts damage
  - Medium tier: up to 5 hearts damage
  - Fast tier: up to 6 hearts damage

💥 **Knockoff Mechanics** - Chance to knock riders off horses
  - 5% at zero momentum
  - 20% at full momentum
  - Scales smoothly between

🛡️ **Shields** - A raised shield blocks a lance hit from the victim's frontal arc (hits from behind go through, like vanilla). Blocking costs shield durability (Unbreaking reduces it, unbreakable shields never wear) and can still knock the blocker back.

🔊 **Knockback** - Hit players are knocked back in the lance direction

🎵 **Configurable Sounds** - Customize hit, knockoff, shield, and break sounds (played at the point of impact)

⚙️ **Admin Commands** - `/jousting reload` to reload configuration

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Copy `target/JoustingPlugin-1.0.0.jar` to your server's `plugins/` folder

3. Restart your server

4. Edit `plugins/JoustingPlugin/config.yml` to customize settings (optional)

5. Run `/jousting reload` to apply any config changes

## Configuration

Edit `config.yml` to customize:

```yaml
# Lance tiers (any material listed here counts as a lance)
lance-tiers:
  IRON_SPEAR:
    damage-bonus: 0.0
    max-uses: 10
  GOLDEN_SPEAR:
    damage-bonus: 1.0
    max-uses: 14
  DIAMOND_SPEAR:
    damage-bonus: 2.0
    max-uses: 18

# Global fallback for any tier missing its own max-uses
lance-max-uses: 10

# Cooldown (ms) after a lance hit or a shield block
lance-cooldown-ms: 1500

# Horse movement-speed attribute thresholds
medium-tier-speed-threshold: 0.2
high-tier-speed-threshold: 0.28

# Maximum damage values (hearts)
low-tier-max-damage: 3.0
medium-tier-max-damage: 5.0
high-tier-max-damage: 6.0

# Momentum
minimum-run-speed: 0.15          # blocks/tick needed to keep building momentum
momentum-gain-multiplier: 0.5    # fraction of movement credited (0.5 = 2x slower recharge)
minimum-momentum-distance: 5.0   # below this, a hit deals no lance damage
full-momentum-distance: 15.0     # at this, momentum (and the bar) is full
momentum-decay-per-tick: 0.5     # bleed rate when the horse slows down

# Absolute ceiling (hearts) on a single lance hit — no one-shots
final-damage-hard-cap: 9.0

# Shield interaction
shield:
  durability-damage: 50          # per blocked hit, before Unbreaking
  cooldown-on-block: true        # blocked hit still starts the lance cooldown
  knockback-on-block: true       # blocked hit still knocks the blocker back

# Knockoff probabilities (percent)
knockoff-chance-zero-momentum: 5
knockoff-chance-full-momentum: 20

# Knockback strength
knockback-strength: 0.5

# Sound effects (Sound enum names)
sounds:
  hit: "ENTITY_PLAYER_ATTACK_STRONG"
  knockoff: "ENTITY_ITEM_BREAK"
  shield: "ITEM_SHIELD_BLOCK"
  break: "ENTITY_ITEM_BREAK"

# Message the attacker with damage/momentum details on every lance hit
debug-damage: false
```

## Commands

- `/jousting` - Shows available commands
- `/jousting reload` - Reloads the plugin configuration
- `/jousting give <iron|gold|diamond>` - Gives you a lance of that tier (warns if that tier was removed from `lance-tiers`)

## Permissions

- `jousting.admin` - Permission to use admin commands (default: op)
- `jousting.use` - Permission to wield lances in combat (default: true)

## How It Works

1. **Mount a horse** and hold a spear (any configured lance material)
2. **Ride the horse** in a run-up to build momentum (circling doesn't count)
3. **Hit another player** with your lance
4. **Damage scales** with momentum, horse speed tier, and lance tier
5. **Shields block** frontal hits (durability cost); hits from behind go through
6. **Chance to knockoff** a mounted target, scaling from 5% to 20%
7. **Momentum resets** after each hit

## Requirements

- Paper 1.21.11 or later (needs the craftable spear items)
- Java 21 or later

## Author

k4nefr-cmd

## Forked by

GorrGodSlayer (AKA Alex)

## Horse Combat System (added 2026-06-18)

Implemented by GorrGodSlayer

- Three lance tiers as distinct items — Iron (`IRON_SPEAR`), Gold (`GOLDEN_SPEAR`), Diamond (`DIAMOND_SPEAR`)
- Lance durability + breaking (lance becomes decorative when spent)
- Post-hit / post-block lance cooldown
- Shield interaction (shield takes durability, knockback, cooldown; no health damage on a block)
- Three horse speed tiers (slow / medium / fast → 3 / 5 / 6 caps)
- Momentum reset on stop, hit, or dismount
- Momentum visual **BossBar**
- Lance damage ignores Strength/Sharpness and is hard-capped so no one-hit kills
- PvP mode (foot target) vs Jousting mode (mounted target, dismount chance)
- `/jousting give <iron|gold|diamond>`

Build & test locally with `mvn clean verify`.

## RECENT CHANGES

### 2026-07-14 (balance pass)

0. **Root-cause fix:** 1.21.11 spears attack with their own `SPEAR` damage type, not `ENTITY_ATTACK`, so the jousting handler never fired in-game — no momentum-scaled damage, no momentum reset, and the vanilla spear's built-in dismount ran unchecked (why knockoff felt like 100%). The handler now accepts spear and melee damage types, and the vanilla dismount is suppressed so the plugin's knockoff roll is the only thing that unseats a rider.
1. Near-miss reset: galloping past a player who comes within 0.5 blocks of the horse without being hit spends the charge — a whiffed pass can't wheel around at full momentum
1a. Momentum recharges 2× slower: new `momentum-gain-multiplier` (default 0.5) credits only half of each block ridden (applies even on servers with an older config.yml, since the key defaults when absent)
2. Broken lances are now truly unusable: any attack with a spent lance is cancelled outright (previously it fell through to vanilla melee damage)
2. Default full-momentum knockoff chance lowered from 70% to 20% (existing servers: update `knockoff-chance-full-momentum` in their config.yml by hand — the plugin won't overwrite it)
3. Momentum no longer builds (and the bar no longer shows) while riding an untamed horse

### 2026-07-14

1. Shields now only block lance hits from the victim's frontal 180° arc, matching vanilla — a lance in the back goes through
2. Shield durability respects the Unbreakable flag (no wear) and the Unbreaking enchantment (reduced wear)
3. Anti-circling momentum: momentum is capped by straight-line displacement from the charge's start point, so orbiting a target can't build a full charge
4. Hit and knockoff sounds play at the victim (the point of impact), not at the attacker
5. `/jousting give` warns when the given tier isn't configured in `lance-tiers`
6. Removed dead code (unused lance PDC marker, `isLance`/`isBroken`, `CooldownManager.remaining`, legacy momentum accessors)
7. Test suite expanded to 43 tests (shield arc math, Unbreaking durability, charge/anti-circling momentum math)

### Earlier

1. Lance cooldown no longer cancels all melee damage — being on cooldown, holding a broken lance, or lacking momentum now falls through to vanilla melee instead of cancelling the hit outright
2. Momentum boss bar now tracks the damage curve — the bar previously glitched into showing positive progress when the actual damage increase was null
3. Dismounting no longer resets lance cooldown
4. Config validation — fixed bugs where lance durability of 0 broke every lance upon mounting
5. Armor stands are no longer a valid target
6. Momentum tracks the horse's position, not the rider's (fixed the momentum bar jumping around)
