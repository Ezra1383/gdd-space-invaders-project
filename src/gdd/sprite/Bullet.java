package gdd.sprite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * An enemy bullet (Stage 5a). Moves along a floating-point velocity so it can
 * travel at any angle — the basis for danmaku patterns (see {@link BulletPattern}).
 *
 * The image is a small coloured dot generated once in memory and shared: a
 * danmaku screen can hold hundreds of bullets, so per-bullet image work would be
 * wasteful. Drawing it ourselves (rather than loading a PNG) also keeps the
 * hitbox exact and avoids AWT's fragile PNG scaling.
 */
public class Bullet extends Sprite {

    /** Rendered/collision size of a bullet, in pixels. */
    public static final int SIZE = 14;

    private static final Image IMG = makeImage();

    private double fx;
    private double fy;
    private final double vx;
    private final double vy;

    /**
     * @param cx centre x of the bullet at spawn
     * @param cy centre y of the bullet at spawn
     * @param vx x velocity in px/frame
     * @param vy y velocity in px/frame
     */
    public Bullet(double cx, double cy, double vx, double vy) {
        this(cx, cy, vx, vy, null);
    }

    /**
     * @param sprite projectile art rotated to the travel angle; falls back to
     *               the generated dot when the pack has no matching sprite.
     */
    public Bullet(double cx, double cy, double vx, double vy, Image sprite) {
        Image img = sprite != null ? sprite : IMG;
        setImage(img);
        this.fx = cx - img.getWidth(null) / 2.0;
        this.fy = cy - img.getHeight(null) / 2.0;
        this.vx = vx;
        this.vy = vy;
        this.x = (int) fx;
        this.y = (int) fy;
    }

    private static Image makeImage() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 80, 60));        // warm outer glow
        g.fillOval(0, 0, SIZE, SIZE);
        g.setColor(new Color(255, 225, 190));      // bright core
        g.fillOval(SIZE / 4, SIZE / 4, SIZE / 2, SIZE / 2);
        g.dispose();
        return img;
    }

    @Override
    public void act() {
        fx += vx;
        fy += vy;
        x = (int) fx;
        y = (int) fy;
    }

    /**
     * Collides on a tight centred core rather than the full image.
     *
     * Rotated projectile art sits on a diagonal-sized canvas, so its bounds
     * include a lot of transparent padding — without this a bullet would hit
     * well outside the visible sprite, which reads as unfair in a danmaku.
     */
    @Override
    public boolean collidesWith(Sprite other) {
        if (other == null || !isVisible() || !other.isVisible()) {
            return false;
        }
        int w = getImage().getWidth(null);
        int h = getImage().getHeight(null);
        int cw = Math.max(4, (int) (w * 0.55));
        int ch = Math.max(4, (int) (h * 0.55));
        int cx = x + (w - cw) / 2;
        int cy = y + (h - ch) / 2;
        return cx < other.getX() + other.getImage().getWidth(null)
                && cx + cw > other.getX()
                && cy < other.getY() + other.getImage().getHeight(null)
                && cy + ch > other.getY();
    }
}
