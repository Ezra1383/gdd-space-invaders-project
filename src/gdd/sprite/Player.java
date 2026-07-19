package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;
import java.awt.event.KeyEvent;

public class Player extends Sprite {

    private static final int START_X = 60;
    private static final int START_Y = 340;
    // Danmaku dodging zone: the player roams the left third of the board in 2D.
    private static final int ZONE_MIN_X = 20;
    private static final int ZONE_MAX_X = BOARD_WIDTH / 3;
    private int currentSpeed = 4;

    public Player() {
        initPlayer();
    }

    private void initPlayer() {
        // Scaled via Images (ImageIO-backed) to dodge AWT's buggy PNG scaling.
        setImage(Images.scaledBy(IMG_PLAYER, SCALE_FACTOR));

        setX(START_X);
        setY(START_Y);
    }

    public int getSpeed() {
        return currentSpeed;
    }

    public int setSpeed(int speed) {
        if (speed < 1) {
            speed = 1; // Ensure speed is at least 1
        }
        this.currentSpeed = speed;
        return currentSpeed;
    }

    public void act() {
        x += dx;
        y += dy;

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
