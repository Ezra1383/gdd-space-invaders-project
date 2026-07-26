package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class Player extends Sprite {

    private static final int START_X = 60;
    private static final int START_Y = 340;
    // Danmaku dodging zone: the player roams the left third of the board in 2D.
    public static final int ZONE_MIN_X = 20;
    private static final int ZONE_MAX_X = BOARD_WIDTH / 3;
    // Final loop: the roles swap and the player takes the boss's right-side
    // arena, roaming the right two-fifths of the board.
    private static final int FINAL_ZONE_MIN_X = BOARD_WIDTH * 3 / 5;
    private static final int FINAL_ZONE_MAX_X = BOARD_WIDTH - 20;
    private int zoneMinX = ZONE_MIN_X;
    /**
     * Widened for the mirror duel. Vertical rays only work as a threat if
     * there's room to dodge sideways, and you-left / Nemesis-right reads as a
     * duel rather than a siege. Public so the boss can place its vertical rays
     * against the same bounds the player is actually confined to.
     */
    public static final int DUEL_ZONE_MAX_X = BOARD_WIDTH / 2;
    private int zoneMaxX = ZONE_MAX_X;
    private static final int MAX_SPEED = 12; // cap so stacked drops stay controllable
    private int currentSpeed = 4;

    // Weapon tiers (1..MAX_WEAPON). Weapon powerups award a pip; a tier costs
    // more pips the higher it is (1,2,3,4 -> 10 drops to max), so firepower
    // ramps across the run instead of maxing in the first biome.
    public static final int MAX_WEAPON = 5;
    private int weaponLevel = 1;
    private int weaponPips = 0; // progress toward the next tier

    // Corruption (0..MAX): after Nemesis falls, special shards turn the player
    // into Nemesis — redder each stage, and each stage strengthens the Ray. At
    // MAX the transformation completes (fully red, widened movement zone).
    public static final int MAX_CORRUPTION = 5;
    private int corruption = 0;

    // Clones: ghostly copies of the ship that fly alongside. Each adds firepower
    // and one soaks a lethal hit before the player itself can die. Offsets are
    // relative to the player's top-left; index 0 is the first clone gained
    // (below, slightly behind), index 1 the second (above, slightly behind).
    public static final int MAX_CLONES = 2;
    private int clones = 0;
    private static final int[][] CLONE_OFFSETS = {
        {-20, 30},  // clone 1: below us, a little to the left
        {-20, -30}, // clone 2: above us, a little to the left
    };

    // Shields: hit-absorbing charges that sit BEHIND the clones — a hit spends a
    // clone first, and only once none are left does a shield break. Stacks up to
    // MAX_SHIELDS.
    public static final int MAX_SHIELDS = 3;
    private int shields = 0;

    // Grazing: a near-miss band around the hull, wider than the kill hitbox.
    // Bullets that pass within it (but don't hit) score points, danmaku-style.
    public static final int GRAZE_RADIUS = 26;

    // Blue side-jet frames cut from spites.png (1P), keyed transparent.
    // Level flight, plus bank-up / bank-down frames used when moving vertically.
    private static final int[] SHIP_KEYS = {0x003663, 0x464646, 0x000000};
    private static final BufferedImage SHIP_LEVEL = Images.tile(IMG_SPRITES, 56, 8, 24, 21, 2, SHIP_KEYS, 40);
    private static final BufferedImage SHIP_UP = Images.tile(IMG_SPRITES, 88, 8, 24, 21, 2, SHIP_KEYS, 40);
    private static final BufferedImage SHIP_DOWN = Images.tile(IMG_SPRITES, 120, 8, 24, 21, 2, SHIP_KEYS, 40);

    // Each pose pre-tinted toward red for every corruption stage [0..MAX], plus
    // a left-facing (mirrored) set for the final loop, where you turn to face
    // your blue past self on the left.
    private final BufferedImage[] level = tintStages(SHIP_LEVEL);
    private final BufferedImage[] up = tintStages(SHIP_UP);
    private final BufferedImage[] down = tintStages(SHIP_DOWN);
    private final BufferedImage[] levelL = flipAll(level);
    private final BufferedImage[] upL = flipAll(up);
    private final BufferedImage[] downL = flipAll(down);
    private boolean facingLeft = false;

    public Player() {
        initPlayer();
    }

    private static BufferedImage[] tintStages(BufferedImage base) {
        BufferedImage[] out = new BufferedImage[MAX_CORRUPTION + 1];
        for (int i = 0; i <= MAX_CORRUPTION; i++) {
            out[i] = Images.tintTowardRed(base, i / (double) MAX_CORRUPTION);
        }
        return out;
    }

    private static BufferedImage[] flipAll(BufferedImage[] src) {
        BufferedImage[] out = new BufferedImage[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = Images.flippedH(src[i]);
        }
        return out;
    }

    private void initPlayer() {
        // Blue side-jet from the sprite sheet (faces right), keyed transparent.
        setImage(level[0]);

        setX(START_X);
        setY(START_Y);
    }

    /** Grazing hitbox: a small dot at the ship's centre, danmaku-style. */
    private static final int HITBOX_RADIUS = 6; // ~12px box vs the 48x42 hull

    @Override
    public java.awt.Rectangle getHitbox() {
        int cx = x + getImage().getWidth(null) / 2;
        int cy = y + getImage().getHeight(null) / 2;
        return new java.awt.Rectangle(cx - HITBOX_RADIUS, cy - HITBOX_RADIUS,
                HITBOX_RADIUS * 2, HITBOX_RADIUS * 2);
    }

    public int getCorruption() {
        return corruption;
    }

    public boolean isFullyCorrupt() {
        return corruption >= MAX_CORRUPTION;
    }

    /**
     * The final loop: the player takes the boss's right-side arena. Called once
     * the transformation completes and you must fight your blue past self.
     */
    public void enterFinalArena() {
        zoneMinX = FINAL_ZONE_MIN_X;
        zoneMaxX = FINAL_ZONE_MAX_X;
        x = (FINAL_ZONE_MIN_X + FINAL_ZONE_MAX_X) / 2;
        y = BOARD_HEIGHT / 2;
        facingLeft = true; // turn to face your blue past self
    }

    /** Takes on one more stage of Nemesis. At full, the arena opens up. */
    public void corrupt() {
        if (corruption < MAX_CORRUPTION) {
            corruption++;
            if (corruption == MAX_CORRUPTION) {
                zoneMaxX = DUEL_ZONE_MAX_X; // the fully-become boss roams wider
            }
        }
    }

    public int getClones() {
        return clones;
    }

    /** Adds a clone, up to {@link #MAX_CLONES}. */
    public void addClone() {
        if (clones < MAX_CLONES) {
            clones++;
        }
    }

    /**
     * Spends a clone to absorb an otherwise-lethal hit. Returns true if a clone
     * took the blow (the player survives), false if none were left (the player
     * dies as normal).
     */
    public boolean absorbWithClone() {
        if (clones > 0) {
            clones--;
            return true;
        }
        return false;
    }

    public int getShields() {
        return shields;
    }

    /** Adds a shield, up to {@link #MAX_SHIELDS}. */
    public void addShield() {
        if (shields < MAX_SHIELDS) {
            shields++;
        }
    }

    /**
     * Breaks a shield to absorb a hit — checked only after {@link #absorbWithClone}
     * fails, so clones are always spent first. Returns true if a shield took it.
     */
    public boolean absorbWithShield() {
        if (shields > 0) {
            shields--;
            return true;
        }
        return false;
    }

    /**
     * Screen offset of clone {@code i} from the player's top-left. Mirrored when
     * the ship faces left (final loop) so clones always trail behind it.
     */
    public int[] cloneOffset(int i) {
        int[] base = CLONE_OFFSETS[i];
        int ox = facingLeft ? -base[0] : base[0];
        return new int[] {ox, base[1]};
    }

    public int getSpeed() {
        return currentSpeed;
    }

    /** Widens the movement zone for the mirror duel, and back again after it. */
    public void setDuelZone(boolean duel) {
        zoneMaxX = duel ? DUEL_ZONE_MAX_X : ZONE_MAX_X;
    }

    public int getZoneMaxX() {
        return zoneMaxX;
    }

    public int getWeaponLevel() {
        return weaponLevel;
    }

    /** Pips needed to advance FROM this level to the next (rises per tier). */
    private static int pipsForNext(int level) {
        return level * 2; // Lv1->2 = 1, Lv2->3 = 2, ... Lv4->5 = 4 (10 total)
    }

    /** Pips banked toward the next tier, and how many that tier needs. */
    public int getWeaponPips() {
        return weaponPips;
    }

    public int getWeaponPipsNeeded() {
        return weaponLevel < MAX_WEAPON ? pipsForNext(weaponLevel) : 0;
    }

    /** Awards one pip; advances a tier once enough pips are banked. */
    public void upgradeWeapon() {
        if (weaponLevel >= MAX_WEAPON) {
            return; // already maxed — extra drops carry no further
        }
        weaponPips++;
        if (weaponPips >= pipsForNext(weaponLevel)) {
            weaponPips = 0;
            weaponLevel++;
        }
    }

    /** Jumps straight to the top tier (play-test loadout for the boss key). */
    public void maxWeapon() {
        weaponLevel = MAX_WEAPON;
        weaponPips = 0;
    }

    public int setSpeed(int speed) {
        if (speed < 1) {
            speed = 1; // Ensure speed is at least 1
        }
        if (speed > MAX_SPEED) {
            speed = MAX_SPEED; // cap stacked powerup boosts
        }
        this.currentSpeed = speed;
        return currentSpeed;
    }

    public void act() {
        x += dx;
        y += dy;

        // Bank the ship based on vertical movement (skip while dying so the
        // explosion sprite isn't overwritten). The frame is picked from the
        // corruption stage, so the jet reddens as the player becomes Nemesis;
        // in the final loop it faces left, toward the blue past self.
        if (!isDying()) {
            BufferedImage[] lv = facingLeft ? levelL : level;
            BufferedImage[] u = facingLeft ? upL : up;
            BufferedImage[] d = facingLeft ? downL : down;
            if (dy < 0) {
                setImage(u[corruption]);
            } else if (dy > 0) {
                setImage(d[corruption]);
            } else {
                setImage(lv[corruption]);
            }
        }

        int rightBound = zoneMaxX - getImage().getWidth(null);
        int bottomBound = BOARD_HEIGHT - BORDER_BOTTOM - getImage().getHeight(null);

        if (x <= zoneMinX) {
            x = zoneMinX;
        }

        if (x >= rightBound) {
            x = rightBound;
        }

        if (y <= BORDER_TOP) {
            y = BORDER_TOP;
        }

        if (y >= bottomBound) {
            y = bottomBound;
        }
    }

    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == KeyEvent.VK_UP) {
            dy = -currentSpeed;
        }

        if (key == KeyEvent.VK_DOWN) {
            dy = currentSpeed;
        }

        if (key == KeyEvent.VK_LEFT) {
            dx = -currentSpeed;
        }

        if (key == KeyEvent.VK_RIGHT) {
            dx = currentSpeed;
        }
    }

    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == KeyEvent.VK_UP) {
            dy = 0;
        }

        if (key == KeyEvent.VK_DOWN) {
            dy = 0;
        }

        if (key == KeyEvent.VK_LEFT) {
            dx = 0;
        }

        if (key == KeyEvent.VK_RIGHT) {
            dx = 0;
        }
    }
}
