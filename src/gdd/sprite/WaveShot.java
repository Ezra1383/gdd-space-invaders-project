package gdd.sprite;

import gdd.Faction;
import gdd.GifSprites;
import java.awt.image.BufferedImage;

/**
 * A Kla'ed Wave — a tall energy wall that sweeps the arena right to left.
 *
 * The pack's Wave art is a broad arc fired "forward" from a ship that faces up;
 * once the loader rotates it to our left-flying orientation it becomes a
 * full-height vertical wall, which is exactly what the Kla'ed boss needs to
 * fence off a lane. It thickens across its six frames, then holds the final,
 * fully-formed wall for the rest of the crossing.
 *
 * It extends {@link Bullet} so the scene's existing enemy-bullet pipeline moves,
 * draws, culls and player-collides it with no special handling.
 */
public class WaveShot extends Bullet {

    private static final int TICKS_PER_FRAME = 5;
    /**
     * Hitbox as a fraction of the sprite. The arc leaves the canvas corners
     * empty, so the box is narrow horizontally — but it stays nearly
     * full-height, because being a wall is the entire point of the attack.
     */
    private static final double CORE_W = 0.50;
    private static final double CORE_H = 0.92;

    private final BufferedImage[] frames;
    private int idx = 0;
    private int tick = 0;

    /**
     * @param height how tall the wall should be — the boss sizes this to the
     *               lane it is trying to close.
     */
    public WaveShot(Faction faction, double cx, double cy, double speed, int height) {
        super(cx, cy, -speed, 0, firstFrame(faction, height));
        this.frames = GifSprites.wave(faction, height);
    }

    private static BufferedImage firstFrame(Faction faction, int height) {
        BufferedImage[] f = GifSprites.wave(faction, height);
        return f.length > 0 ? f[0] : null; // null falls back to Bullet's dot
    }

    @Override
    public void act() {
        super.act();
        if (frames.length == 0) {
            return;
        }
        if (++tick >= TICKS_PER_FRAME) {
            tick = 0;
            if (idx < frames.length - 1) {
                idx++; // hold the last frame: the wall persists as it crosses
                setImage(frames[idx]);
            }
        }
    }

    @Override
    public boolean collidesWith(Sprite other) {
        if (other == null || !isVisible() || !other.isVisible()) {
            return false;
        }
        int w = getImage().getWidth(null);
        int h = getImage().getHeight(null);
        int cw = Math.max(4, (int) (w * CORE_W));
        int ch = Math.max(4, (int) (h * CORE_H));
        int cx = x + (w - cw) / 2;
        int cy = y + (h - ch) / 2;
        return cx < other.getX() + other.getImage().getWidth(null)
                && cx + cw > other.getX()
                && cy < other.getY() + other.getImage().getHeight(null)
                && cy + ch > other.getY();
    }
}
