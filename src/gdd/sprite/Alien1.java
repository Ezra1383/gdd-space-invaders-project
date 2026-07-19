package gdd.sprite;

import static gdd.Global.*;
import javax.swing.ImageIcon;

public class Alien1 extends Enemy {

    private Bomb bomb;

    public Alien1(int x, int y) {
        super(x, y);
        // Fly in from the right (Enemy.act() moves along dx until it reaches
        // homeX, then holds — see Stage 5b).
        this.dx = -4;
        this.hp = 4; // takes a few hits before dying
        // Danmaku firing (Stage 5a). Wave-assigned patterns come in Stage 5c;
        // for now Alien1 fans bullets at the player on a timer.
        this.firePattern = BulletPattern.FAN;
        this.fireInterval = 110;
        this.fireCooldown = 110;
    }

    private void initEnemy(int x, int y) {

        this.x = x;
        this.y = y;

        bomb = new Bomb(x, y);

        var ii = new ImageIcon(IMG_ENEMY);

        // Scale the image to use the global scaling factor
        var scaledImage = ii.getImage().getScaledInstance(ii.getIconWidth() * SCALE_FACTOR,
                ii.getIconHeight() * SCALE_FACTOR,
                java.awt.Image.SCALE_SMOOTH);
        setImage(scaledImage);
    }

    // act() is inherited from Enemy (x += dx).

    public Bomb getBomb() {

        return bomb;
    }

    public class Bomb extends Sprite {

        private boolean destroyed;

        public Bomb(int x, int y) {

            initBomb(x, y);
        }

        private void initBomb(int x, int y) {

            setDestroyed(true);

            this.x = x;
            this.y = y;

            var bombImg = "src/images/bomb.png";
            var ii = new ImageIcon(bombImg);
            setImage(ii.getImage());
        }

        public void setDestroyed(boolean destroyed) {

            this.destroyed = destroyed;
        }

        public boolean isDestroyed() {

            return destroyed;
        }

        public void act() {
            // Not wired up yet — real enemy-fire patterns land in Stage 5.
        }
    }
}
