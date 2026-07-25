package gdd.powerup;

import static gdd.Global.*;
import gdd.Images;
import gdd.sprite.Player;
import java.awt.image.BufferedImage;

/**
 * Weapon powerup (drops from kills). Collecting it bumps the player's weapon
 * tier, changing their bullet type/pattern. Drawn as the blue Offense pickup —
 * a spinning icon that reads as "firepower".
 */
public class WeaponUp extends PowerUp {

    // The blue pickup's 15-frame spin animation (32x32 cells).
    private static final BufferedImage[] FRAMES = Images.strip(IMG_POWERUP_WEAPON, 32, 32, 1);
    private static final int TICKS_PER_FRAME = 3;
    private int idx = 0;
    private int tick = 0;

    public WeaponUp(int x, int y) {
        super(x, y);
        setImage(FRAMES[0]);
    }

    @Override
    public void act() {
        this.x -= 2; // drift left with the scroll, toward the player
        if (++tick >= TICKS_PER_FRAME) {
            tick = 0;
            idx = (idx + 1) % FRAMES.length; // spin
            setImage(FRAMES[idx]);
        }
    }

    @Override
    public void upgrade(Player player) {
        player.upgradeWeapon();
        this.die();
    }
}
