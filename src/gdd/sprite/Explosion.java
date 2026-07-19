package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;

public class Explosion extends Sprite {


    public Explosion(int x, int y) {

        initExplosion(x, y);
    }

    private void initExplosion(int x, int y) {

        this.x = x;
        this.y = y;

        // Scaled via Images (ImageIO-backed) to dodge AWT's buggy PNG scaling.
        setImage(Images.scaledBy(IMG_EXPLOSION, SCALE_FACTOR));
    }

    public void act() {

    }


}
