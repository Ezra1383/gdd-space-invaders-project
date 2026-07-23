package gdd.sprite;

import static gdd.Global.*;
import gdd.Images;
import java.awt.Image;
import java.util.HashMap;
import java.util.Map;

/**
 * An explosion cut from {@code spites.png}, in the ship's own colours — a blue
 * burst for the player, a red one for Nemesis. Three frames (burst → ring →
 * smaller ring) play once, then it vanishes.
 *
 * Plugs into the scene's explosion list like {@link Destruction}: the draw loop
 * calls {@link #visibleCountDown()} each frame, which advances the animation and
 * hides the sprite after the last frame.
 */
public class SheetBlast extends Sprite {

    private static final int[] KEYS = {0x003663, 0x464646, 0x000000};
    private static final int TICKS_PER_FRAME = 4;

    // Frame cells in the sheet. The 2P (red) copies sit exactly 88 rows below
    // the 1P (blue) ones — the same offset as the ship livery.
    private static final int RED_DY = 88;
    private static final int[][] CELLS = {
        {335, 11, 16, 10}, // burst
        {268, 37, 10, 6},  // ring
        {283, 37, 8, 6},   // smaller ring
    };

    private static final Map<String, Image[]> CACHE = new HashMap<>();

    private final Image[] frames;
    private final int cx;
    private final int cy;
    private int idx = 0;
    private int tick = 0;

    public SheetBlast(int cx, int cy, boolean red, int scale) {
        this.cx = cx;
        this.cy = cy;
        this.frames = framesFor(red, scale);
        centreOn(frames[0]);
    }

    private static Image[] framesFor(boolean red, int scale) {
        return CACHE.computeIfAbsent((red ? "r" : "b") + scale, k -> {
            int dy = red ? RED_DY : 0;
            Image[] out = new Image[CELLS.length];
            for (int i = 0; i < CELLS.length; i++) {
                int[] c = CELLS[i];
                out[i] = Images.tile(IMG_SPRITES, c[0], c[1] + dy, c[2], c[3], scale, KEYS, 40);
            }
            return out;
        });
    }

    private void centreOn(Image img) {
        setImage(img);
        this.x = cx - img.getWidth(null) / 2;
        this.y = cy - img.getHeight(null) / 2;
    }

    @Override
    public void act() {
        // driven by visibleCountDown() from the draw loop
    }

    @Override
    public void visibleCountDown() {
        if (++tick >= TICKS_PER_FRAME) {
            tick = 0;
            idx++;
        }
        if (idx >= frames.length) {
            visible = false;
            return;
        }
        centreOn(frames[idx]); // frames shrink, so re-centre each one
    }
}
