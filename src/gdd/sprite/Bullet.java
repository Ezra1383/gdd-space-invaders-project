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
        this.fx = cx - SIZE / 2.0;
        this.fy = cy - SIZE / 2.0;
        this.vx = vx;
        this.vy = vy;
        this.x = (int) fx;
        this.y = (int) fy;
        setImage(IMG);
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
}
