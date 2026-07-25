package gdd.powerup;

import static gdd.Global.*;
import gdd.Images;
import gdd.sprite.Player;
import java.awt.image.BufferedImage;

/**
 * Clone powerup (a defensive drop). Collecting one spawns a 60%-transparent
 * copy of the ship that flies alongside the player: it adds firepower (fires
 * with you) and acts as extra HP — the next lethal hit kills a clone instead of
 * you. Up to {@link Player#MAX_CLONES} can be held at once. Drawn as the Clones
 * Offense/Defensive spin strip.
 */
public class Clone extends PowerUp {

    // The clone pickup's 15-frame spin animation (32x32 cells).
    private static final BufferedImage[] FRAMES = Images.strip(IMG_POWERUP_CLONE, 32, 32, 1);
    private static final int TICKS_PER_FRAME = 3;
    private int idx = 0;
    private int tick = 0;

    public Clone(int x, int y) {
        super(x, y);
        setImage(FRAMES[0]);
    }

    @Override
    public void act() {
        this.x -= 2; // drift left toward the player, like the other drops
        if (++tick >= TICKS_PER_FRAME) {
            tick = 0;
            idx = (idx + 1) % FRAMES.length; // spin
            setImage(FRAMES[idx]);
        }
    }

    @Override
    public void upgrade(Player player) {
        player.addClone();
        this.die();
    }
}
