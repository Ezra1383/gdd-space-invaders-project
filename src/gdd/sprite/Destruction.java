package gdd.sprite;

import gdd.Faction;
import gdd.GifSprites;
import java.awt.image.BufferedImage;

/**
 * A one-shot ship destruction animation, played where an enemy died, using that
 * ship's own wreck art from its faction's pack. Hooks into the scene's existing
 * explosion list: the draw loop calls
 * {@link #visibleCountDown()} each frame, which advances the animation and hides
 * the sprite once the last frame has played.
 */
public class Destruction extends Sprite {

    private static final int TICKS_PER_FRAME = 2;

    private final BufferedImage[] frames;
    private int idx = 0;
    private int tick = 0;

    public Destruction(Faction faction, String shipName, int cx, int cy, int size) {
        this.frames = GifSprites.destruction(faction, shipName, size);
        if (frames.length == 0) {
            visible = false;
            return;
        }
        setImage(frames[0]);
        this.x = cx - frames[0].getWidth() / 2;
        this.y = cy - frames[0].getHeight() / 2;
    }

    @Override
    public void act() {
        // driven by visibleCountDown() from the draw loop
    }

    @Override
    public void visibleCountDown() {
        if (frames.length == 0) {
            visible = false;
            return;
        }
        if (++tick >= TICKS_PER_FRAME) {
            tick = 0;
            idx++;
        }
        if (idx >= frames.length) {
            visible = false;
            return;
        }
        setImage(frames[idx]);
    }
}
