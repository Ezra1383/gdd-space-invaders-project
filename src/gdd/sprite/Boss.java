package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;
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

    public Boss(String name, int x, int y, int hp) {
        super(x, y);
        this.name = name;
        this.hp = hp;
        this.maxHp = hp;
        this.dx = -3; // fly-in speed
        setImage(Images.scaledTinted(IMG_ENEMY, SCALE_FACTOR * 3.0, 1.0, 0.35, 0.35));
    }

    public String getName() {
        return name;
    }

    @Override
    public void act() {
        if (hitFlash > 0) {
            hitFlash--;
        }
        if (!arrived) {
            // Fly in from the right to the hold column.
            x += dx;
            if (dx < 0 && x <= homeX) {
                x = homeX;
                arrived = true;
                dx = 0;
            }
        } else {
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
        List<Bullet> volley = pattern.fire(cx, cy, playerX, playerY, shotCount);
        shotCount++;
        return volley;
    }
}
