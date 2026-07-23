package gdd.sprite;

import gdd.Faction;
import gdd.GifSprites;
import java.awt.image.BufferedImage;
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

    // Animated ship sprite (Nairan base+engine loop).
    protected BufferedImage[] frames = new BufferedImage[0];
    protected int animTick = 0;
    protected int animIdx = 0;
    protected static final int ANIM_TICKS_PER_FRAME = 5;
    protected Faction faction = Faction.NAIRAN;
    protected String shipName = "Fighter";
    protected int spriteSize = 48;
    protected ProjectileType ammo = ProjectileType.NAIRAN_BOLT;

    // One-shot overlays: weapon flash while firing, shield flare when hit.
    protected BufferedImage[] weaponFrames = new BufferedImage[0];
    protected BufferedImage[] shieldFrames = new BufferedImage[0];
    private int weaponIdx = -1;
    private int shieldIdx = -1;

    public Enemy(EnemyType type, int x, int y) {
        this.x = x;
        this.y = y;
        this.hp = type.hp;
        this.maxHp = type.hp;
        this.dx = type.dx;
        this.firePattern = type.pattern;
        this.fireInterval = type.fireInterval;
        this.fireCooldown = type.fireInterval;
        this.faction = type.faction;
        this.shipName = type.ship;
        this.spriteSize = type.spriteSize;
        this.ammo = type.ammo;
        this.frames = GifSprites.ship(faction, type.ship, type.spriteSize);
        this.weaponFrames = GifSprites.weapons(faction, type.ship, type.spriteSize);
        this.shieldFrames = GifSprites.shields(faction, type.ship, type.spriteSize);
        if (frames.length > 0) {
            setImage(frames[0]);
        }
    }

    /** Constructor for special enemies (e.g. Boss) that configure themselves. */
    protected Enemy(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Faction getFaction() {
        return faction;
    }

    public String getShipName() {
        return shipName;
    }

    public int getSpriteSize() {
        return spriteSize;
    }

    /** Advances the idle/engine animation loop. */
    protected void advanceAnim() {
        if (isDying() || frames.length == 0) {
            return;
        }
        if (++animTick >= ANIM_TICKS_PER_FRAME) {
            animTick = 0;
            animIdx = (animIdx + 1) % frames.length;
            setImage(frames[animIdx]);
        }
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

    /** Applies one point of damage. Returns true if it destroys the enemy. */
    public boolean hit() {
        return hit(1);
    }

    /** Applies {@code damage} HP of damage. Returns true if it destroys the enemy. */
    public boolean hit(int damage) {
        hitFlash = 4;
        hp -= damage;
        boolean dead = hp <= 0;
        if (!dead && shieldFrames.length > 0) {
            shieldIdx = 0; // flare the shield instead of a flat white flash
        }
        return dead;
    }

    /** Current weapon-flash frame, or null when not firing. Aligns with the ship. */
    public BufferedImage getWeaponOverlay() {
        if (weaponIdx < 0 || weaponIdx >= weaponFrames.length || isDying()) {
            return null;
        }
        return weaponFrames[weaponIdx];
    }

    /** Current shield-flare frame, or null. Draw centred on the ship. */
    public BufferedImage getShieldOverlay() {
        if (shieldIdx < 0 || shieldIdx >= shieldFrames.length || isDying()) {
            return null;
        }
        return shieldFrames[shieldIdx];
    }

    /** Advances the one-shot weapon/shield overlays; call once per frame. */
    protected void advanceOverlays() {
        if (weaponIdx >= 0) {
            weaponIdx++;
            if (weaponIdx >= weaponFrames.length) {
                weaponIdx = -1;
            }
        }
        if (shieldIdx >= 0) {
            shieldIdx++;
            if (shieldIdx >= shieldFrames.length) {
                shieldIdx = -1;
            }
        }
    }

    /** Starts the weapon-fire animation (called when a volley goes out). */
    protected void triggerWeaponAnim() {
        if (weaponFrames.length > 0) {
            weaponIdx = 0;
        }
    }

    public int getHitFlash() {
        return hitFlash;
    }

    /**
     * Ticks this enemy's fire timer and, when it elapses, fires a volley aimed
     * at (playerX, playerY). Returns the new bullets, or an empty list.
     *
     * Holds fire until the ship has reached its slot, so a wave never shoots
     * from off-screen right before the player can see what's coming.
     */
    public List<Bullet> maybeFire(int playerX, int playerY) {
        if (firePattern == null || !isVisible() || isDying() || !arrived) {
            return Collections.emptyList();
        }
        if (--fireCooldown > 0) {
            return Collections.emptyList();
        }
        fireCooldown = fireInterval;
        int cx = x + getImage().getWidth(null) / 2;
        int cy = y + getImage().getHeight(null) / 2;
        List<Bullet> volley = firePattern.fire(cx, cy, playerX, playerY, shotCount, ammo);
        shotCount++;
        triggerWeaponAnim();
        return volley;
    }

    public void act() {

        if (hitFlash > 0) {
            hitFlash--;
        }

        advanceAnim();
        advanceOverlays();

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
