package gdd;

import static gdd.Global.*;

import java.awt.Image;

/**
 * The player's gun sprites, shared rather than owned by the scene — the final
 * boss fires the player's own weapons back at them, so both sides need these.
 *
 * Cut from {@code spites.png} and colour-keyed transparent.
 */
public final class Weapons {

    /** Background colours in the sheet: field blue, grey, black. */
    private static final int[] KEYS = {0x003663, 0x464646, 0x000000};

    public static final Image PELLET = Images.tile(IMG_SPRITES, 246, 12, 12, 7, 2, KEYS, 40);
    /** Round and symmetric, so angled shots never look reversed. */
    public static final Image ORB = Images.tile(IMG_SPRITES, 288, 36, 10, 9, 3, KEYS, 40);
    public static final Image COMET = Images.tile(IMG_SPRITES, 380, 7, 29, 19, 2, KEYS, 40);

    public static final double SHOT_SPEED = 12;

    private Weapons() {
    }
}
