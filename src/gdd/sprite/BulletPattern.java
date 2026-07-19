package gdd.sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * A danmaku firing pattern (Stage 5a). Decouples bullet <em>trajectories</em>
 * from the enemy that fires them: an enemy just holds a pattern and a timer, and
 * asks the pattern to produce a volley of {@link Bullet}s.
 *
 * {@code shot} is the enemy's running volley count, which patterns like SPIRAL
 * use to rotate the aim between volleys.
 */
public enum BulletPattern {
    /** One bullet straight at the player. */
    AIMED,
    /** A spread of bullets fanned around the aim direction. */
    FAN,
    /** A full 360° ring, ignoring the player. */
    RING,
    /** A rotating ring — successive volleys twist, tracing a spiral. */
    SPIRAL,
    /** A few bullets whose aim sweeps back and forth over time. */
    WAVE;

    private static final double SPEED = 3.2; // px/frame; dodgeable at player speed

    public List<Bullet> fire(int ox, int oy, int px, int py, int shot) {
        List<Bullet> out = new ArrayList<>();
        double aim = Math.atan2(py - oy, px - ox);
        switch (this) {
            case AIMED:
                out.add(bullet(ox, oy, aim));
                break;
            case FAN: {
                int n = 5;
                double spread = Math.toRadians(18);
                for (int i = 0; i < n; i++) {
                    double a = aim + (i - (n - 1) / 2.0) * spread;
                    out.add(bullet(ox, oy, a));
                }
                break;
            }
            case RING: {
                int n = 12;
                for (int i = 0; i < n; i++) {
                    out.add(bullet(ox, oy, i * 2 * Math.PI / n));
                }
                break;
            }
            case SPIRAL: {
                int n = 4;
                double base = shot * Math.toRadians(23);
                for (int i = 0; i < n; i++) {
                    out.add(bullet(ox, oy, base + i * 2 * Math.PI / n));
                }
                break;
            }
            case WAVE: {
                double a = aim + Math.sin(shot * 0.5) * Math.toRadians(40);
                out.add(bullet(ox, oy, a));
                out.add(bullet(ox, oy, a + Math.toRadians(12)));
                out.add(bullet(ox, oy, a - Math.toRadians(12)));
                break;
            }
        }
        return out;
    }

    private static Bullet bullet(int ox, int oy, double angle) {
        return new Bullet(ox, oy, Math.cos(angle) * SPEED, Math.sin(angle) * SPEED);
    }
}
