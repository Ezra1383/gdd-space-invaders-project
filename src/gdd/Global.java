package gdd;

public class Global {
    private Global() {
        // Prevent instantiation
    }

    public static final int SCALE_FACTOR = 3; // Scaling factor for sprites

    public static final int BOARD_WIDTH = 716; // Doubled from 358
    public static final int BOARD_HEIGHT = 700; // Doubled from 350
    public static final int BORDER_BOTTOM = 60; // Doubled from 30
    public static final int BORDER_TOP = 10; // Doubled from 5

    public static final int GROUND = 580; // Doubled from 290
    public static final int BOMB_HEIGHT = 10; // Doubled from 5

    public static final int ALIEN_HEIGHT = 24; // Doubled from 12
    public static final int ALIEN_WIDTH = 24; // Doubled from 12
    public static final int ALIEN_INIT_X = 300; // Doubled from 150
    public static final int ALIEN_INIT_Y = 10; // Doubled from 5
    public static final int ALIEN_GAP = 30; // Gap between aliens

    public static final int GO_DOWN = 30; // Doubled from 15
    public static final int NUMBER_OF_ALIENS_TO_DESTROY = 24;
    public static final int CHANCE = 5;
    public static final int DELAY = 17;
    public static final int PLAYER_WIDTH = 30; // Doubled from 15
    public static final int PLAYER_HEIGHT = 20; // Doubled from 10

    // Images
    public static final String IMG_ENEMY = "src/images/alien.png";
    public static final String IMG_PLAYER = "src/images/player.png";
    public static final String IMG_SHOT = "src/images/shot.png";
    public static final String IMG_BOMB = "src/images/bomb.png";
    public static final String IMG_EXPLOSION = "src/images/explosion.png";
    public static final String IMG_TITLE = "src/images/title.png";
    // Institutional crests, shown in the title screen's top corners.
    public static final String IMG_LOGO_UNIVERSITY =
            "src/images/Logos/Assumption_University_of_Thailand_(logo).png";
    public static final String IMG_LOGO_FACULTY = "src/images/Logos/VMES-Logo-BG-White.png";
    public static final String IMG_POWERUP_SPEEDUP = "src/images/powerup-s.png";
    public static final String IMG_SPRITES = "src/images/spites.png"; // sprite sheet
    // Offense pickup spin strips (15 frames of 32x32): the player's weapon
    // level-up (blue) and Nemesis's corruption shard (red).
    public static final String IMG_POWERUP_WEAPON = "src/images/PowerUps/Offense/BluePickUp.png";
    public static final String IMG_POWERUP_CORRUPTION = "src/images/PowerUps/Offense/NemesisPickUp.png";
    // Clone pickup spin strip (15 frames of 32x32): a defensive drop that grants
    // an extra ship — extra HP (soaks a lethal hit) and extra firepower.
    public static final String IMG_POWERUP_CLONE = "src/images/PowerUps/Defensive/Clones.png";
    // Shield pickup spin strip (15 frames of 32x32): a defensive drop granting a
    // hit-absorbing shield that only takes damage once all clones are gone.
    public static final String IMG_POWERUP_SHIELD = "src/images/PowerUps/Defensive/Sheilds.png";
}
