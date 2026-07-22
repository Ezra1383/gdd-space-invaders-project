package gdd.sprite;

import static gdd.Global.*;
import gdd.GifSprites;
import java.util.Collections;
import java.util.List;

/**
 * A boss (Stage 7). A big, high-HP enemy that flies in, patrols vertically, and
 * escalates its bullet pattern as its HP drops (FAN → SPIRAL → RING). Because it
 * sits in the normal enemies list, it holds back the next wave until killed.
 *
 * Placeholder art: a large red-tinted base sprite; swap for real boss art later.
 */
public class Boss extends Enemy {

    private static final int PATROL_MARGIN = 40;
    private static final int PATROL_SPEED = 2;

    private final String name;
    private int patrolDir = 1;

    /** The Nairan flagship used for every boss. */
    public static final String SHIP = "Dreadnought";
    private static final int SPRITE_SIZE = 130;

    // Signature beam attack: charge (telegraph) → fire → cool down. The boss
    // holds still for the whole cycle so the beam is a readable, dodgeable line.
    private static final int BEAM_INTERVAL = 420;
    private static final int BEAM_CHARGE = 60;
    private static final int BEAM_FIRE = 75;
    /** Half-height of the damaging beam band. */
    public static final int BEAM_HALF_THICKNESS = 20;

    private int beamTimer = 0;
    private int beamPhase = 0; // 0 = idle, 1 = charging, 2 = firing

    public Boss(String name, int x, int y, int hp) {
        super(x, y);
        this.name = name;
        this.hp = hp;
        this.maxHp = hp;
        this.dx = -3; // fly-in speed
        this.shipName = SHIP;
        this.spriteSize = SPRITE_SIZE;
        this.ammo = ProjectileType.BOLT;
        this.frames = GifSprites.ship(SHIP, SPRITE_SIZE);
        this.weaponFrames = GifSprites.weapons(SHIP, SPRITE_SIZE);
        this.shieldFrames = GifSprites.shields(SHIP, SPRITE_SIZE);
        if (frames.length > 0) {
            setImage(frames[0]);
        }
    }

    public boolean isBeamCharging() {
        return arrived && beamPhase == 1;
    }

    public boolean isBeamFiring() {
        return arrived && beamPhase == 2;
    }

    /** Vertical centre of the beam (the boss's own centre line). */
    public int getBeamCenterY() {
        return y + getImage().getHeight(null) / 2;
    }

    public String getName() {
        return name;
    }

    @Override
    public void act() {
        if (hitFlash > 0) {
            hitFlash--;
        }
        advanceAnim();
        advanceOverlays();
        if (!arrived) {
            // Fly in from the right to the hold column.
            x += dx;
            if (dx < 0 && x <= homeX) {
                x = homeX;
                arrived = true;
                dx = 0;
            }
        } else {
            // Beam cycle. While charging or firing the boss stops moving so the
            // beam stays a fixed, dodgeable line.
            beamTimer++;
            if (beamPhase == 0 && beamTimer >= BEAM_INTERVAL) {
                beamPhase = 1;
                beamTimer = 0;
            } else if (beamPhase == 1 && beamTimer >= BEAM_CHARGE) {
                beamPhase = 2;
                beamTimer = 0;
            } else if (beamPhase == 2 && beamTimer >= BEAM_FIRE) {
                beamPhase = 0;
                beamTimer = 0;
            }
            if (beamPhase != 0) {
                return; // hold position during the beam
            }

            // Patrol up and down.
            y += patrolDir * PATROL_SPEED;
            int h = getImage().getHeight(null);
            if (y <= PATROL_MARGIN) {
                y = PATROL_MARGIN;
                patrolDir = 1;
            } else if (y >= BOARD_HEIGHT - PATROL_MARGIN - h) {
                y = BOARD_HEIGHT - PATROL_MARGIN - h;
                patrolDir = -1;
            }
        }
    }

    @Override
    public List<Bullet> maybeFire(int playerX, int playerY) {
        if (!isVisible() || isDying() || !arrived) {
            return Collections.emptyList();
        }
        if (beamPhase != 0) {
            return Collections.emptyList(); // busy with the signature beam
        }
        if (--fireCooldown > 0) {
            return Collections.emptyList();
        }
        // Escalating attack phases based on remaining HP.
        double frac = hp / (double) maxHp;
        BulletPattern pattern;
        if (frac > 0.66) {
            pattern = BulletPattern.FAN;
            fireCooldown = 55;
        } else if (frac > 0.33) {
            pattern = BulletPattern.SPIRAL; // rapid rotating spiral
            fireCooldown = 7;
        } else {
            pattern = BulletPattern.RING;
            fireCooldown = 40;
        }
        int cx = x + getImage().getWidth(null) / 2;
        int cy = y + getImage().getHeight(null) / 2;
        List<Bullet> volley = pattern.fire(cx, cy, playerX, playerY, shotCount, ammo);
        shotCount++;
        triggerWeaponAnim();
        return volley;
    }
}
