package gdd.sprite;

/**
 * Data-driven enemy definitions. Each type configures a plain {@link Enemy}:
 * durability, entry speed, bullet pattern, fire rate, plus which Nairan ship
 * sprite it wears and how big to draw it.
 *
 * Adding an enemy is one row here. Swapping art is one string.
 */
public enum EnemyType {
    //             hp  dx  pattern                fireInt  ship             size heavy  ammo
    SCOUT         ( 2, -7, BulletPattern.AIMED,       70, "Scout",           44, false, ProjectileType.BOLT),
    FIGHTER       ( 4, -4, BulletPattern.FAN,        110, "Fighter",         48, false, ProjectileType.BOLT),
    FRIGATE       ( 7, -3, BulletPattern.WAVE,       120, "Frigate",         56, false, ProjectileType.ROCKET),
    BOMBER        ( 9, -3, BulletPattern.RING,       150, "Bomber",          54, false, ProjectileType.TORPEDO),
    /** Rare heavy that shows up in late waves — a mini-boss, always solo. */
    BATTLECRUISER (26, -2, BulletPattern.RING,       100, "Battlecruiser",   92, true,  ProjectileType.ROCKET);

    public final int hp;
    public final int dx;
    public final BulletPattern pattern;
    public final int fireInterval;
    public final String ship;
    public final int spriteSize;
    /** Heavies only ever spawn alone, never as a multi-ship formation. */
    public final boolean heavy;
    /** Which Nairan projectile this ship fires. */
    public final ProjectileType ammo;

    EnemyType(int hp, int dx, BulletPattern pattern, int fireInterval,
              String ship, int spriteSize, boolean heavy, ProjectileType ammo) {
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
            return FIGHTER;
        }
    }
}
