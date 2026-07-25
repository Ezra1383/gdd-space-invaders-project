package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;
import gdd.Weapons;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Your blue past self, fought in the final loop once you have become Nemesis.
 * It flies in the left zone — the player's old ground — weaving and firing the
 * player's own weapons back to the right, at you.
 *
 * It cannot die: {@link #hit} only flashes it. Its fire escalates the longer the
 * duel runs, so the doomed fight always ends the one way it can — with you,
 * Nemesis, falling to the hero you once were.
 */
public class BlueSelf extends Sprite {

    private static final int[] KEYS = {0x003663, 0x464646, 0x000000};
    private static final Image JET = Images.tile(IMG_SPRITES, 56, 8, 24, 21, 2, KEYS, 40);

    // Its roaming band — the left side of the board.
    private static final int MIN_X = 30;
    private static final int MAX_X = BOARD_WIDTH * 2 / 5;

    private final Random rng = new Random();
    private int hitFlash = 0;
    private int age = 0;
    private int fireCooldown = 40;

    // Wander target it eases toward, re-rolled periodically (dodging weave).
    private int targetX;
    private int targetY;
    private int retarget = 0;

    public BlueSelf(int x, int y) {
        setImage(JET);
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
    }

    public int getHitFlash() {
        return hitFlash;
    }

    /** Flashes on a hit but never dies — the past cannot be killed. */
    public void hit() {
        hitFlash = 5;
    }

    @Override
    public void act() {
        // target overload requires (px, py); this shouldn't be called directly
    }

    /** Weaves toward a wandering point, biased away from the player's fire line. */
    public void act(int playerY) {
        age++;
        if (hitFlash > 0) {
            hitFlash--;
        }
        if (--retarget <= 0) {
            retarget = 24 + rng.nextInt(40);
            targetX = MIN_X + rng.nextInt(Math.max(1, MAX_X - MIN_X));
            // Prefer a row away from the player's, so it reads as dodging.
            int t;
            do {
                t = BORDER_TOP + rng.nextInt(BOARD_HEIGHT - BORDER_BOTTOM - BORDER_TOP);
            } while (Math.abs(t - playerY) < 90 && rng.nextInt(3) != 0);
            targetY = t;
        }
        int speed = 3;
        x += Integer.signum(targetX - x) * Math.min(speed, Math.abs(targetX - x));
        y += Integer.signum(targetY - y) * Math.min(speed, Math.abs(targetY - y));
    }

    /**
     * Fires the player's weapons rightward at (px, py). The volley thickens and
     * the cadence quickens as the duel drags on, so survival becomes impossible.
     */
    public List<Bullet> maybeFire(int px, int py) {
        if (--fireCooldown > 0) {
            return List.of();
        }
        double intensity = Math.min(1.0, age / 2400.0); // ramps over ~40s
        fireCooldown = (int) (34 - 18 * intensity);     // 34 → 16 frames
        int cx = x + getImage().getWidth(null);
        int cy = y + getImage().getHeight(null) / 2;
        double speed = 5.0 + 2.0 * intensity;
        double aim = Math.atan2(py - cy, px - cx);
        int spread = 1 + (int) Math.round(intensity * 2); // 1 → 3 shots

        List<Bullet> out = new ArrayList<>();
        for (int i = 0; i < spread; i++) {
            double a = aim + (i - (spread - 1) / 2.0) * Math.toRadians(11);
            out.add(new Bullet(cx, cy, Math.cos(a) * speed, Math.sin(a) * speed, Weapons.ORB));
        }
        return out;
    }
}
