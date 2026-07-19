package gdd.powerup;

import static gdd.Global.*;
import gdd.Images;
import gdd.sprite.Player;

/**
 * Weapon powerup (drops from kills). Collecting it bumps the player's weapon
 * tier, changing their bullet type/pattern. Drawn as the blue orb from the
 * sprite sheet so it reads as "firepower".
 */
public class WeaponUp extends PowerUp {

    private static final int[] KEYS = {0x003663, 0x464646, 0x000000};

    public WeaponUp(int x, int y) {
        super(x, y);
        setImage(Images.tile(IMG_SPRITES, 262, 34, 22, 12, 2, KEYS, 40));
    }

    @Override
    public void act() {
        this.x -= 2; // drift left with the scroll, toward the player
    }

    @Override
    public void upgrade(Player player) {
        player.upgradeWeapon();
        this.die();
    }
}
