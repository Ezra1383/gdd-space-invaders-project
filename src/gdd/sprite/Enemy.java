package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;
import java.util.Collections;
import java.util.List;

public class Enemy extends Sprite {

    // Danmaku firing (Stage 5a). A null pattern means this enemy never shoots.
    protected BulletPattern firePattern = null;
    protected int fireInterval = 0;   // frames between volleys
    protected int fireCooldown = 0;   // frames until the next volley
    protected int shotCount = 0;      // volleys fired so far (drives SPIRAL etc.)

    // Fly-in-and-hold (Stage 5b). The enemy drifts left (dx) until it reaches
    // homeX, then stops and idles in place until killed.
    private static final double BOB_AMP = 8; // gentle vertical idle bob
    protected int homeX = Integer.MIN_VALUE;  // set via setHomeX(); unset = drift off
    protected int homeY;
    protected boolean arrived = false;
    protected int idleTick = 0;

    // Durability (Stage 5b). Enemies take a few hits; hitFlash drives a brief
    // white flash for feedback.
    protected int hp = 1;
    protected int maxHp = 1;
    protected int hitFlash = 0;

    public Enemy(EnemyType type, int x, int y) {
        this.x = x;
        this.y = y;
        this.hp = type.hp;
        this.maxHp = type.hp;
        this.dx = type.dx;
        this.firePattern = type.pattern;
        this.fireInterval = type.fireInterval;
        this.fireCooldown = type.fireInterval;
        // Recoloured/scaled variant of the shared base sprite (swap art later).
        setImage(Images.scaledTinted(IMG_ENEMY, SCALE_FACTOR * type.scale,
                type.tintR, type.tintG, type.tintB));
    }

    /** Constructor for special enemies (e.g. Boss) that configure themselves. */
    protected Enemy(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setHomeX(int homeX) {
        this.homeX = homeX;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    /** Applies one hit. Returns true if this hit destroys the enemy. */
    public boolean hit() {
        hitFlash = 4;
        hp--;
        return hp <= 0;
    }

    public int getHitFlash() {
        return hitFlash;
    }

    /**
     * Ticks this enemy's fire timer and, when it elapses, fires a volley aimed
     * at (playerX, playerY). Returns the new bullets, or an empty list.
     */
    public List<Bullet> maybeFire(int playerX, int playerY) {
        if (firePattern == null || !isVisible() || isDying()) {
            return Collections.emptyList();
        }
        if (--fireCooldown > 0) {
            return Collections.emptyList();
        }
        fireCooldown = fireInterval;
        int cx = x + getImage().getWidth(null) / 2;
        int cy = y + getImage().getHeight(null) / 2;
        List<Bullet> volley = firePattern.fire(cx, cy, playerX, playerY, shotCount);
        shotCount++;
        return volley;
    }

    public void act() {

        if (hitFlash > 0) {
            hitFlash--;
        }

        if (!arrived) {
            // Fly in from the right until reaching the home column, then lock.
            x += dx;
            if (dx < 0 && x <= homeX) {
                x = homeX;
                homeY = y;
                arrived = true;
                dx = 0;
            }
        } else {
            // Hold position with a gentle vertical bob so it reads as alive.
            idleTick++;
            y = homeY + (int) Math.round(Math.sin(idleTick * 0.05) * BOB_AMP);
        }
    }
}
