package gdd.sprite;

import gdd.Faction;

/**
 * Data-driven enemy definitions. Each type configures a plain {@link Enemy}:
 * durability, entry speed, bullet pattern, fire rate, plus which faction's ship
 * sprite it wears and how big to draw it.
 *
 * Types are grouped by {@link Faction} — one faction per biome, so a biome swap
 * changes the whole roster and its projectiles at once. Adding an enemy is one
 * row here; swapping art is one string.
 *
 * Two ships in the packs are deliberately unused: each faction's Dreadnought is
 * reserved for {@link Boss}, and Kla'ed's Support ship has neither a Weapons nor
 * a Shield layer, so it would read as inert next to the rest.
 */
public enum EnemyType {
    // Biome 1 — Nairan.
    //                      faction        hp  dx  pattern              fireInt  ship             size heavy  ammo
    NAIRAN_SCOUT         (Faction.NAIRAN,  2, -7, BulletPattern.AIMED,     70, "Scout",           44, false, ProjectileType.NAIRAN_BOLT),
    NAIRAN_FIGHTER       (Faction.NAIRAN,  4, -4, BulletPattern.FAN,      110, "Fighter",         48, false, ProjectileType.NAIRAN_BOLT),
    NAIRAN_FRIGATE       (Faction.NAIRAN,  7, -3, BulletPattern.WAVE,     120, "Frigate",         56, false, ProjectileType.NAIRAN_ROCKET),
    NAIRAN_BOMBER        (Faction.NAIRAN,  9, -3, BulletPattern.RING,     150, "Bomber",          54, false, ProjectileType.NAIRAN_TORPEDO),
    /** Rare heavy that shows up in late waves — a mini-boss, always solo. */
    NAIRAN_BATTLECRUISER (Faction.NAIRAN, 26, -2, BulletPattern.RING,     100, "Battlecruiser",   92, true,  ProjectileType.NAIRAN_ROCKET),

    // Biome 2 — Kla'ed. Tuned a notch above their Nairan counterparts, since
    // the biome only opens after the player has cleared a boss and powered up.
    KLAED_SCOUT          (Faction.KLAED,   3, -7, BulletPattern.AIMED,     60, "Scout",           44, false, ProjectileType.KLAED_BULLET),
    KLAED_FIGHTER        (Faction.KLAED,   5, -4, BulletPattern.FAN,      100, "Fighter",         48, false, ProjectileType.KLAED_BULLET),
    KLAED_FRIGATE        (Faction.KLAED,   8, -3, BulletPattern.WAVE,     110, "Frigate",         56, false, ProjectileType.KLAED_BIG_BULLET),
    KLAED_BOMBER         (Faction.KLAED,  10, -3, BulletPattern.RING,     140, "Bomber",          54, false, ProjectileType.KLAED_TORPEDO),
    /** Slow, tanky, and fires straight down the player's line. */
    KLAED_TORPEDO_SHIP   (Faction.KLAED,   8, -3, BulletPattern.AIMED,     85, "Torpedo Ship",    52, false, ProjectileType.KLAED_TORPEDO),
    KLAED_BATTLECRUISER  (Faction.KLAED,  30, -2, BulletPattern.SPIRAL,    14, "Battlecruiser",   92, true,  ProjectileType.KLAED_BIG_BULLET);

    public final Faction faction;
    public final int hp;
    public final int dx;
    public final BulletPattern pattern;
    public final int fireInterval;
    public final String ship;
    public final int spriteSize;
    /** Heavies only ever spawn alone, never as a multi-ship formation. */
    public final boolean heavy;
    /** Which of the faction's projectiles this ship fires. */
    public final ProjectileType ammo;

    EnemyType(Faction faction, int hp, int dx, BulletPattern pattern, int fireInterval,
              String ship, int spriteSize, boolean heavy, ProjectileType ammo) {
        this.faction = faction;
        this.hp = hp;
        this.dx = dx;
        this.pattern = pattern;
        this.fireInterval = fireInterval;
        this.ship = ship;
        this.spriteSize = spriteSize;
        this.heavy = heavy;
        this.ammo = ammo;
    }

    /** Resolves a spawn-type string to an EnemyType, tolerant of unknown names. */
    public static EnemyType fromString(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return NAIRAN_FIGHTER;
        }
    }
}
