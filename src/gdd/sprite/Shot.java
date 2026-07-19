package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;

public class Shot extends Sprite {

    private static final int H_SPACE = 20;
    private static final int SPEED = 20;

    public Shot() {
    }

    public Shot(int x, int y) {

        initShot(x, y);
    }

    private void initShot(int x, int y) {

        // Scaled via Images (ImageIO-backed) to dodge AWT's buggy PNG scaling.
        setImage(Images.scaledBy(IMG_SHOT, SCALE_FACTOR));

        setX(x + H_SPACE);
        setY(y);
    }

    public void act() {
        x += SPEED;
    }
}
