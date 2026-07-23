package gdd.sprite;

import gdd.Faction;
import gdd.GifSprites;
import java.awt.image.BufferedImage;

/**
 * Enemy bullet sprites, taken from each faction's Projectiles folder so a
 * biome's shots match its ships.
 *
 * Each is pre-rendered at {@link #STEPS} rotations so a bullet fired at any
 * angle (fan, ring, spiral) points where it's actually travelling.
 *
 * Each pack's "Ray" is deliberately absent — at 90x190 it's a sustained beam,
 * used as the boss's signature attack rather than a travelling projectile.
 * Kla'ed's "Wave" (320x320) is likewise an area effect, not a bullet.
 */
public enum ProjectileType {
    NAIRAN_BOLT(Faction.NAIRAN, "Bolt", 18),
    NAIRAN_ROCKET(Faction.NAIRAN, "Rocket", 26),
    NAIRAN_TORPEDO(Faction.NAIRAN, "Torpedo", 30),

    KLAED_BULLET(Faction.KLAED, "Bullet", 18),
    KLAED_BIG_BULLET(Faction.KLAED, "Big Bullet", 24),
    KLAED_TORPEDO(Faction.KLAED, "Torpedo", 30);

    /** Rotation steps around the full circle. */
    public static final int STEPS = 32;

    public final Faction faction;
    public final String gif;
    public final int size;

    ProjectileType(Faction faction, String gif, int size) {
        this.faction = faction;
        this.gif = gif;
        this.size = size;
    }

    /** Sprite rotated to match a travel angle in radians. */
    public BufferedImage imageFor(double angle) {
        BufferedImage[] rot = GifSprites.projectileRotations(faction, gif, size, STEPS);
        if (rot.length == 0) {
            return null;
        }
        double turns = angle / (2 * Math.PI);
        int idx = (int) Math.round(turns * STEPS) % STEPS;
        if (idx < 0) {
            idx += STEPS;
        }
        return rot[idx];
    }
}
