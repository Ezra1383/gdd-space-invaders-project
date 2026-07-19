package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;
import java.awt.Image;
import java.awt.event.KeyEvent;

public class Player extends Sprite {

    private static final int START_X = 60;
    private static final int START_Y = 340;
    // Danmaku dodging zone: the player roams the left third of the board in 2D.
    private static final int ZONE_MIN_X = 20;
    private static final int ZONE_MAX_X = BOARD_WIDTH / 3;
    private static final int MAX_SPEED = 12; // cap so stacked drops stay controllable
    private int currentSpeed = 4;

    // Weapon tiers (1..MAX_WEAPON); weapon powerups bump this up.
    public static final int MAX_WEAPON = 5;
    private int weaponLevel = 1;

    // Blue side-jet frames cut from spites.png (1P), keyed transparent.
    // Level flight, plus bank-up / bank-down frames used when moving vertically.
    private static final int[] SHIP_KEYS = {0x003663, 0x464646, 0x000000};
    private static final Image SHIP_LEVEL = Images.tile(IMG_SPRITES, 56, 8, 24, 21, 2, SHIP_KEYS, 40);
    private static final Image SHIP_UP = Images.tile(IMG_SPRITES, 88, 8, 24, 21, 2, SHIP_KEYS, 40);
    private static final Image SHIP_DOWN = Images.tile(IMG_SPRITES, 120, 8, 24, 21, 2, SHIP_KEYS, 40);

    public Player() {
        initPlayer();
    }

    private void initPlayer() {
        // Blue side-jet from the sprite sheet (faces right), keyed transparent.
        setImage(SHIP_LEVEL);

        setX(START_X);
        setY(START_Y);
    }

    public int getSpeed() {
        return currentSpeed;
    }

    public int getWeaponLevel() {
        return weaponLevel;
    }

    public void upgradeWeapon() {
        if (weaponLevel < MAX_WEAPON) {
            weaponLevel++;
        }
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
        // explosion sprite isn't overwritten).
        if (!isDying()) {
            if (dy < 0) {
                setImage(SHIP_UP);
            } else if (dy > 0) {
                setImage(SHIP_DOWN);
            } else {
                setImage(SHIP_LEVEL);
            }
        }

        int rightBound = ZONE_MAX_X - getImage().getWidth(null);
        int bottomBound = BOARD_HEIGHT - BORDER_BOTTOM - getImage().getHeight(null);

        if (x <= ZONE_MIN_X) {
            x = ZONE_MIN_X;
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
