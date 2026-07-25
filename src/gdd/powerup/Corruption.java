package gdd.powerup;

import static gdd.Global.*;
import gdd.Images;
import gdd.sprite.Player;
import java.awt.image.BufferedImage;

/**
 * A shard of Nemesis, dropped by kills once Nemesis has fallen. Collecting one
 * pushes the player another stage toward becoming Nemesis: redder, with a
 * stronger forward Ray. Drawn as the Nemesis Offense pickup — a spinning red
 * icon that reads as a piece of the enemy you just became.
 */
public class Corruption extends PowerUp {

    // The Nemesis pickup's 15-frame spin animation (32x32 cells).
    private static final BufferedImage[] FRAMES = Images.strip(IMG_POWERUP_CORRUPTION, 32, 32, 1);
    private static final int TICKS_PER_FRAME = 3;
    private int idx = 0;
    private int tick = 0;

    public Corruption(int x, int y) {
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
        player.corrupt();
        this.die();
    }
}
