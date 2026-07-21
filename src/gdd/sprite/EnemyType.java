package gdd.sprite;

/**
 * Data-driven enemy definitions (Stage 6). Each type configures a plain
 * {@link Enemy}: durability, entry speed, bullet pattern, fire rate, and how its
 * sprite is rendered (scale + RGB tint of the shared base sprite). Adding a new
 * enemy is just adding a row here; swapping to custom art later is a one-line
 * sprite change in {@link Enemy}.
 */
public enum EnemyType {
    // hp, dx(entry), pattern,               fireInterval, scale, tintR,tintG,tintB
    GRUNT (4, -4, BulletPattern.FAN,    110, 1.00, 1.0, 1.0, 1.0),  // baseline
    DARTER(2, -7, BulletPattern.AIMED,   70, 0.80, 0.6, 1.0, 0.7),  // small, fast, greenish
    TURRET(9, -3, BulletPattern.RING,   150, 1.35, 1.0, 0.55, 0.5); // big, tanky, reddish

    public final int hp;
    public final int dx;
    public final BulletPattern pattern;
    public final int fireInterval;
    public final double scale;
    public final double tintR;
    public final double tintG;
    public final double tintB;

    EnemyType(int hp, int dx, BulletPattern pattern, int fireInterval,
              double scale, double tintR, double tintG, double tintB) {
        this.hp = hp;
        this.dx = dx;
        this.pattern = pattern;
        this.fireInterval = fireInterval;
        this.scale = scale;
        this.tintR = tintR;
        this.tintG = tintG;
        this.tintB = tintB;
    }

    /** Resolves a spawn-type string to an EnemyType, tolerant of legacy names. */
    public static EnemyType fromString(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return GRUNT; // legacy "Alien1" and anything unknown
        }
    }
}
