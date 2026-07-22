package gdd.sprite;

import gdd.GifSprites;
import java.awt.image.BufferedImage;

/**
 * Enemy bullet sprites from the Nairan pack, replacing the generated dot.
 *
 * Each is pre-rendered at {@link #STEPS} rotations so a bullet fired at any
 * angle (fan, ring, spiral) points where it's actually travelling.
 *
 * "Ray" is deliberately absent — at 90x190 it's a sustained beam, used as the
 * boss's signature attack rather than a travelling projectile.
 */
public enum ProjectileType {
    BOLT("Bolt", 18),
    ROCKET("Rocket", 26),
    TORPEDO("Torpedo", 30);

    /** Rotation steps around the full circle. */
    public static final int STEPS = 32;

    public final String gif;
    public final int size;

    ProjectileType(String gif, int size) {
        this.gif = gif;
        this.size = size;
    }

    /** Sprite rotated to match a travel angle in radians. */
    public BufferedImage imageFor(double angle) {
        BufferedImage[] rot = GifSprites.projectileRotations(gif, size, STEPS);
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
