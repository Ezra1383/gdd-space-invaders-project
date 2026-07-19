package gdd.sprite;

import java.awt.Image;

/**
 * A player shot. Carries a floating-point velocity so weapon tiers can fire at
 * angles (spread), and takes its sprite from the caller so each tier can look
 * different. Spawned centred on (cx, cy).
 */
public class Shot extends Sprite {

    private double fx;
    private double fy;
    private final double vx;
    private final double vy;

    public Shot(int cx, int cy, double vx, double vy, Image img) {
        setImage(img);
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        this.fx = cx - w / 2.0;
        this.fy = cy - h / 2.0;
        this.vx = vx;
        this.vy = vy;
        this.x = (int) fx;
        this.y = (int) fy;
    }

    @Override
    public void act() {
        fx += vx;
        fy += vy;
        x = (int) fx;
        y = (int) fy;
    }
}
