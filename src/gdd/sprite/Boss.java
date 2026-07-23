package gdd.sprite;

import static gdd.Global.*;
import gdd.Faction;
import gdd.GifSprites;
import gdd.Images;
import gdd.Weapons;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A boss: a big, high-HP flagship that flies in, patrols, and holds back the
 * next wave until killed (it sits in the normal enemies list, so the Director's
 * wave gating stalls while it lives).
 *
 * Each biome's boss wears its own faction's Dreadnought and fights its own way
 * — see {@link Moveset}.
 */
public class Boss extends Enemy {

    private static final int PATROL_MARGIN = 40;
    private static final int PATROL_SPEED = 2;

    private final String name;
    private int patrolDir = 1;

    /** Every faction's flagship, used as its biome's boss. */
    public static final String SHIP = "Dreadnought";
    public static final int SPRITE_SIZE = 130;

    /** Half-height of a damaging ray band. Shared by both movesets. */
    public static final int BEAM_HALF_THICKNESS = 20;
    /** Full height of one ray band. */
    private static final int RAY_BAND = 2 * BEAM_HALF_THICKNESS;
    /**
     * Vertical clearance a surviving lane must leave. Comfortably taller than
     * the player sprite (~42px), so no rolled or carved layout is a coin flip.
     */
    private static final int MIN_SAFE_GAP = 70;

    /** How a boss fights. One per biome, so the two fights don't feel re-skinned. */
    public enum Moveset {
        /**
         * Nairan. Periodic rays cut temporary arena-wide bands at rolled
         * positions; the arena is whole again between cycles.
         */
        SWEEP,
        /**
         * Kla'ed. Rays lock in <em>permanently</em> from the top and bottom
         * edges as the boss's HP falls, walling the arena inward; meanwhile it
         * throws Waves down two of the three remaining lanes, leaving one gap.
         */
        CONSTRICT,
        /**
         * Nemesis — the player's own ship, in red, fought last. It remembers
         * every boss already beaten and adds them one at a time as its HP falls:
         * a straight duel first, then SWEEP's rays, then CONSTRICT's walls and
         * waves, and finally vertical rays as reality gives out. Running all of
         * them at once leaves no survivable cell, so they arrive in that order.
         */
        NEMESIS
    }

    // --- NEMESIS: phase thresholds and the vertical ray --------------------

    /** HP fractions at which Nemesis recalls the next boss. */
    private static final double PHASE_SWEEP = 0.75;
    private static final double PHASE_CONSTRICT = 0.50;
    private static final double PHASE_VERTICAL = 0.25;

    /** Half-width of a vertical ray band. */
    public static final int VBEAM_HALF_THICKNESS = 20;
    private static final int VBEAM_INTERVAL = 300;
    private static final int VBEAM_CHARGE = 70;
    private static final int VBEAM_FIRE = 80;
    /** Clearance a surviving column must leave: the player sprite is ~48 wide. */
    private static final int MIN_SAFE_COLUMN = 76;

    private int vbeamTimer = 0;
    private int vbeamPhase = 0;   // 0 = idle, 1 = charging, 2 = firing
    private int vbeamX = Integer.MIN_VALUE;

    /** Nemesis's own guns: the player's top-tier weapons, mirrored back. */
    private static final int NEMESIS_HEAVY_INTERVAL = 95;
    private static final int NEMESIS_SPREAD_INTERVAL = 42;
    private static final double NEMESIS_SHOT_SPEED = 9;
    private int spreadCooldown = NEMESIS_SPREAD_INTERVAL;

    private final Moveset moveset;
    private final Random beamRng;

    // --- SWEEP: temporary rolled bands -----------------------------------

    private static final int BEAM_INTERVAL = 360;
    private static final int BEAM_CHARGE = 75;   // telegraph — time to reposition
    private static final int BEAM_FIRE = 90;     // lethal window
    /** Slow trickle of aimed shots while the bands are up, so the lane isn't a rest stop. */
    private static final int BEAM_FIRE_INTERVAL = 45;

    private int beamTimer = 0;
    private int beamPhase = 0; // 0 = idle, 1 = charging, 2 = firing
    /** Centre lines of the active temporary bands; empty when no cycle is running. */
    private int[] beamYs = new int[0];

    // --- CONSTRICT: permanent rays + wave gauntlet ------------------------

    /**
     * Permanent rays are tied to HP, not elapsed time. Time-based constriction
     * is a death clock: it punishes careful play and eventually makes the fight
     * unwinnable however well you dodge. Driving it from damage bounds the worst
     * case and turns the visible HP bar into a warning of the next squeeze.
     */
    private static final int MAX_PERMANENT_RAYS = 6;
    /** Frames a newly-placed ray blinks as a warning before it turns lethal. */
    private static final int RAY_ARM_FRAMES = 60;
    /** Lanes the free zone is split into; all but one get walled off. */
    private static final int WAVE_SLOTS = 3;
    private static final double WAVE_SPEED = 3.5;
    /** Frames between wave volleys — long enough to leave a bullet window. */
    private static final int WAVE_INTERVAL = 210;
    /** Roughly how long a volley takes to reach the player's side of the board. */
    private static final int WAVE_CROSSING = 130;
    private static final int WAVE_FIRE_INTERVAL = 70; // light fire between volleys

    private int[] permanentYs = new int[0];
    private int armedRays = 0;
    private int armTimer = 0;
    private int waveTimer = WAVE_INTERVAL / 2; // first volley comes early
    private int waveCrossing = 0;
    private int lastOpenSlot = -1;

    /**
     * @param faction which biome's flagship this is — picks the art, the ammo
     *                and the moveset, so each biome's boss reads as its own.
     * @param beamRng stream for ray/wave placement. Kept separate from the
     *                Director's so these rolls never perturb wave generation.
     */
    public Boss(Faction faction, String name, int x, int y, int hp, Random beamRng) {
        super(x, y);
        this.name = name;
        this.faction = faction;
        this.beamRng = beamRng;
        switch (faction) {
            case KLAED:
                this.moveset = Moveset.CONSTRICT;
                break;
            case VOID:
                this.moveset = Moveset.NEMESIS;
                break;
            default:
                this.moveset = Moveset.SWEEP;
        }
        this.hp = hp;
        this.maxHp = hp;
        this.dx = -3; // fly-in speed
        this.spriteSize = SPRITE_SIZE;

        if (moveset == Moveset.NEMESIS) {
            // Not a flagship: the player's own jet, in the sheet's 2P red
            // livery, mirrored to face back at them.
            this.shipName = "Nemesis";
            this.ammo = ProjectileType.KLAED_BULLET; // unused; Nemesis fires our guns
            this.frames = new BufferedImage[]{Images.flippedH(
                    Images.tile(IMG_SPRITES, RED_CELL_X, RED_CELL_Y,
                            RED_CELL_W, RED_CELL_H, NEMESIS_SCALE, SHIP_KEYS, 40))};
            this.spriteSize = frames[0].getWidth();
            setImage(frames[0]);
            return;
        }

        this.shipName = SHIP;
        this.ammo = faction == Faction.KLAED
                ? ProjectileType.KLAED_BULLET : ProjectileType.NAIRAN_BOLT;
        this.frames = GifSprites.ship(faction, SHIP, SPRITE_SIZE);
        this.weaponFrames = GifSprites.weapons(faction, SHIP, SPRITE_SIZE);
        this.shieldFrames = GifSprites.shields(faction, SHIP, SPRITE_SIZE);
        if (frames.length > 0) {
            setImage(frames[0]);
        }
    }

    // The 2P red twin of the player jet sits exactly 88 rows below the blue one
    // in the sheet. The cell is cut short of 21 rows on purpose: below the ship
    // is a block of #790000 that the sheet's background keys don't remove.
    private static final int RED_CELL_X = 56;
    private static final int RED_CELL_Y = 96;
    private static final int RED_CELL_W = 24;
    private static final int RED_CELL_H = 15;
    /** Bigger than the player's 2x — recognisably us, plainly stronger. */
    private static final int NEMESIS_SCALE = 3;
    private static final int[] SHIP_KEYS = {0x003663, 0x464646, 0x000000};

    public String getName() {
        return name;
    }

    public Moveset getMoveset() {
        return moveset;
    }

    /**
     * The 2P red jet, facing right (the player's orientation). The reality-break
     * windows draw clones of it, rotated per edge, so the tears are unmistakably
     * more of us.
     */
    public static BufferedImage redJet() {
        return Images.tile(IMG_SPRITES, RED_CELL_X, RED_CELL_Y,
                RED_CELL_W, RED_CELL_H, NEMESIS_SCALE, SHIP_KEYS, 40);
    }

    /**
     * True once Nemesis reaches its final phase — the fight has outgrown the
     * board and reality starts tearing at the edges. Drives the satellite
     * windows; the in-board attacks stay fully dodgeable without them.
     */
    public boolean isRealityBreaking() {
        return moveset == Moveset.NEMESIS && arrived && hp <= maxHp * PHASE_VERTICAL;
    }

    // --- exposed state for the scene's renderer and hit checks -------------

    public boolean isBeamCharging() {
        return arrived && beamPhase == 1;
    }

    public boolean isBeamFiring() {
        return arrived && beamPhase == 2;
    }

    /** Centre lines of the temporary bands being telegraphed or fired. */
    public int[] getBeamBands() {
        return beamYs;
    }

    /** Centre lines of every permanent ray placed so far. */
    public int[] getPermanentRays() {
        return permanentYs;
    }

    /**
     * How many of {@link #getPermanentRays()} are live. Rays beyond this count
     * are still arming and must render as a warning without killing anything.
     */
    public int getArmedRayCount() {
        return armedRays;
    }

    /** Top of the space the permanent rays have left open. */
    public int getFreeTop() {
        return BORDER_TOP + topRayCount() * RAY_BAND;
    }

    /** Bottom of the space the permanent rays have left open. */
    public int getFreeBottom() {
        return (BOARD_HEIGHT - BORDER_BOTTOM) - bottomRayCount() * RAY_BAND;
    }

    /**
     * Every wall height the Kla'ed boss can ask for, so they can all be decoded
     * up front instead of hitching mid-fight.
     */
    public static int[] waveSlotSizes() {
        int[] out = new int[MAX_PERMANENT_RAYS + 1];
        for (int rays = 0; rays <= MAX_PERMANENT_RAYS; rays++) {
            int top = BORDER_TOP + ((rays + 1) / 2) * RAY_BAND;
            int bottom = (BOARD_HEIGHT - BORDER_BOTTOM) - (rays / 2) * RAY_BAND;
            out[rays] = (int) Math.round((bottom - top) / (double) WAVE_SLOTS);
        }
        return out;
    }

    // Rays alternate top, bottom, top, ... so index parity picks the side.
    private int topRayCount() {
        return (permanentYs.length + 1) / 2;
    }

    private int bottomRayCount() {
        return permanentYs.length / 2;
    }

    // --- act --------------------------------------------------------------

    @Override
    public void act() {
        if (isDying()) {
            return; // frozen while the death sequence tears it apart
        }
        if (hitFlash > 0) {
            hitFlash--;
        }
        advanceAnim();
        advanceOverlays();
        if (!arrived) {
            // Fly in from the right to the hold column.
            x += dx;
            if (dx < 0 && x <= homeX) {
                x = homeX;
                arrived = true;
                dx = 0;
            }
            return;
        }
        switch (moveset) {
            case SWEEP:
                tickSweep();
                break;
            case CONSTRICT:
                tickConstrict(1 - hp / (double) maxHp);
                break;
            default:
                tickNemesis();
        }
        patrol();
    }

    /**
     * Ray cycle. The bands don't depend on where the boss is, so it keeps
     * patrolling throughout — a moving target rather than a parked one.
     */
    private void tickSweep() {
        beamTimer++;
        if (beamPhase == 0 && beamTimer >= BEAM_INTERVAL) {
            beamPhase = 1;
            beamTimer = 0;
            rollBeams(); // fixed for the cycle, so the telegraph is honest
        } else if (beamPhase == 1 && beamTimer >= BEAM_CHARGE) {
            beamPhase = 2;
            beamTimer = 0;
        } else if (beamPhase == 2 && beamTimer >= BEAM_FIRE) {
            beamPhase = 0;
            beamTimer = 0;
            beamYs = new int[0];
        }
    }

    /**
     * Adds permanent rays as the fight progresses, arming the newest after a
     * warning.
     *
     * @param progress 0 = no walls yet, 1 = fully walled in. Kla'ed drives this
     *                 straight off its HP; Nemesis only starts walling once it
     *                 reaches its third phase, so it passes its own ramp.
     */
    private void tickConstrict(double progress) {
        int due = Math.min(MAX_PERMANENT_RAYS,
                (int) Math.floor(progress * (MAX_PERMANENT_RAYS + 1)));
        if (due > permanentYs.length) {
            permanentYs = buildRays(due);
            armTimer = RAY_ARM_FRAMES; // blink before it starts killing
        }
        if (armTimer > 0 && --armTimer == 0) {
            armedRays = permanentYs.length;
        }
        if (waveCrossing > 0) {
            waveCrossing--;
        }
    }

    /**
     * Nemesis recalls one previous boss per phase as its HP falls, so the fight
     * is a run back through everything already beaten. Each system is only added
     * once the player has had a phase to read the last one.
     */
    private void tickNemesis() {
        double frac = hp / (double) maxHp;
        // The two ray axes are mutually exclusive: whichever starts first blocks
        // the other until it finishes. Crossed horizontal and vertical bands cut
        // the arena into pockets too small and too scattered to move between —
        // one axis at a time stays readable and leaves somewhere to go.
        if (frac <= PHASE_SWEEP && vbeamPhase == 0) {
            tickSweep();
        }
        if (frac <= PHASE_CONSTRICT) {
            // Walls ramp across this phase and the next, not off total HP —
            // otherwise three of them would snap in the instant it begins.
            tickConstrict(Math.max(0, (PHASE_CONSTRICT - frac) / PHASE_CONSTRICT));
        }
        if (frac <= PHASE_VERTICAL && beamPhase == 0) {
            tickVertical();
        }
    }

    /**
     * The vertical ray: reality giving out along the other axis. One at a time —
     * the player's duelling zone is only ~338px wide, so a second band would
     * leave a column too narrow to survive in.
     */
    private void tickVertical() {
        vbeamTimer++;
        if (vbeamPhase == 0 && vbeamTimer >= VBEAM_INTERVAL) {
            vbeamPhase = 1;
            vbeamTimer = 0;
            rollVertical();
        } else if (vbeamPhase == 1 && vbeamTimer >= VBEAM_CHARGE) {
            vbeamPhase = 2;
            vbeamTimer = 0;
        } else if (vbeamPhase == 2 && vbeamTimer >= VBEAM_FIRE) {
            vbeamPhase = 0;
            vbeamTimer = 0;
            vbeamX = Integer.MIN_VALUE;
        }
    }

    /**
     * Places the vertical band inside the player's duelling zone, far enough
     * from both its edges that the columns either side always clear
     * {@link #MIN_SAFE_COLUMN}.
     */
    private void rollVertical() {
        int lo = Player.ZONE_MIN_X + VBEAM_HALF_THICKNESS + MIN_SAFE_COLUMN;
        int hi = Player.DUEL_ZONE_MAX_X - VBEAM_HALF_THICKNESS - MIN_SAFE_COLUMN;
        vbeamX = hi <= lo ? (lo + hi) / 2 : lo + beamRng.nextInt(hi - lo + 1);
    }

    public boolean isVerticalCharging() {
        return arrived && vbeamPhase == 1;
    }

    public boolean isVerticalFiring() {
        return arrived && vbeamPhase == 2;
    }

    /** Centre column of the vertical ray, or {@code Integer.MIN_VALUE} if none. */
    public int getVerticalRay() {
        return vbeamX;
    }

    /**
     * Permanent rays stack inward from alternating edges, so the survivable
     * space is always one contiguous band in the middle — a wall closing in
     * rather than a scatter the player has to re-read each time.
     */
    private static int[] buildRays(int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int rank = i / 2;
            out[i] = (i % 2 == 0)
                    ? BORDER_TOP + rank * RAY_BAND + BEAM_HALF_THICKNESS
                    : (BOARD_HEIGHT - BORDER_BOTTOM) - rank * RAY_BAND - BEAM_HALF_THICKNESS;
        }
        return out;
    }

    /** Patrol up and down, staying inside whatever space is still open. */
    private void patrol() {
        y += patrolDir * PATROL_SPEED;
        int h = getImage().getHeight(null);
        int top = Math.max(PATROL_MARGIN, getFreeTop());
        int bottom = Math.min(BOARD_HEIGHT - PATROL_MARGIN, getFreeBottom());
        if (y <= top) {
            y = top;
            patrolDir = 1;
        } else if (y >= bottom - h) {
            y = bottom - h;
            patrolDir = -1;
        }
    }

    /**
     * Places the bands for one SWEEP cycle.
     *
     * The playfield is split into {@code n + 1} lanes and a band is dropped on
     * each divider, jittered by however much room is left once every lane is
     * guaranteed {@link #MIN_SAFE_GAP} of clearance. That keeps placement varied
     * while making it impossible to roll a set with no survivable lane.
     *
     * The whole set then slides as far as it can without pushing a band off the
     * board. Without that the bands always land on the same fractions of the
     * board and the extreme top and bottom stay permanently safe to camp in;
     * sliding uniformly can seal an edge outright, and because every band moves
     * together the guaranteed gaps between them are untouched.
     */
    private void rollBeams() {
        int n = beamCount();
        int top = BORDER_TOP;
        int bottom = BOARD_HEIGHT - BORDER_BOTTOM;
        double lane = (bottom - top) / (double) (n + 1);
        double jitter = Math.max(0, (lane - RAY_BAND - MIN_SAFE_GAP) / 2.0);
        // Slide range that keeps the outermost bands fully on the board even
        // after they take their own jitter.
        double minShift = (top + BEAM_HALF_THICKNESS) - (top + lane - jitter);
        double maxShift = (bottom - BEAM_HALF_THICKNESS) - (top + lane * n + jitter);
        double shift = minShift + beamRng.nextDouble() * Math.max(0, maxShift - minShift);
        beamYs = new int[n];
        for (int i = 0; i < n; i++) {
            double centre = top + lane * (i + 1) + shift;
            beamYs[i] = (int) Math.round(centre + (beamRng.nextDouble() * 2 - 1) * jitter);
        }
    }

    /** Temporary bands rise as the boss weakens: 1 → 2 → 3. */
    private int beamCount() {
        double frac = hp / (double) maxHp;
        if (frac > 0.66) {
            return 1;
        }
        return frac > 0.33 ? 2 : 3;
    }

    // --- firing -----------------------------------------------------------

    @Override
    public List<Bullet> maybeFire(int playerX, int playerY) {
        if (!isVisible() || isDying() || !arrived) {
            return Collections.emptyList();
        }
        switch (moveset) {
            case SWEEP:
                return fireSweep(playerX, playerY);
            case CONSTRICT:
                return fireConstrict(playerX, playerY);
            default:
                return fireNemesis(playerX, playerY);
        }
    }

    /**
     * Nemesis fights with the player's own arsenal at full tier: the heavy comet
     * as its aimed shot, and the tier-4 three-way spread as filler. Both are
     * unmistakably the player's guns pointed the wrong way.
     *
     * From its third phase it also throws Kla'ed's waves, so the walls closing
     * in still come with something to dodge between them.
     */
    private List<Bullet> fireNemesis(int playerX, int playerY) {
        List<Bullet> out = new ArrayList<>();
        double frac = hp / (double) maxHp;

        if (frac <= PHASE_CONSTRICT) {
            if (--waveTimer <= 0) {
                waveTimer = WAVE_INTERVAL;
                waveCrossing = WAVE_CROSSING;
                return fireWaveVolley();
            }
            if (waveCrossing > 0) {
                waveCrossing--;
            }
        }

        int cx = x + getImage().getWidth(null) / 2;
        int cy = y + getImage().getHeight(null) / 2;

        // Heavy: the tier-5 comet, aimed.
        if (--fireCooldown <= 0) {
            fireCooldown = NEMESIS_HEAVY_INTERVAL;
            double a = Math.atan2(playerY - cy, playerX - cx);
            out.add(new Bullet(cx, cy, Math.cos(a) * NEMESIS_SHOT_SPEED,
                    Math.sin(a) * NEMESIS_SHOT_SPEED, Weapons.COMET));
        }
        // Filler: the tier-4 three-way spread, straight back down the board.
        if (--spreadCooldown <= 0) {
            spreadCooldown = NEMESIS_SPREAD_INTERVAL;
            double s = NEMESIS_SHOT_SPEED * 0.7;
            out.add(new Bullet(cx, cy, -s, 0, Weapons.ORB));
            out.add(new Bullet(cx, cy, -s, -2.4, Weapons.ORB));
            out.add(new Bullet(cx, cy, -s, 2.4, Weapons.ORB));
        }
        if (!out.isEmpty()) {
            triggerWeaponAnim();
        }
        return out;
    }

    private List<Bullet> fireSweep(int playerX, int playerY) {
        if (--fireCooldown > 0) {
            return Collections.emptyList();
        }
        BulletPattern pattern;
        if (beamPhase != 0) {
            // Bands are up. A slow aimed trickle keeps the surviving lane from
            // being a rest stop, without stacking a full pattern on top of an
            // already-shrunken arena.
            pattern = BulletPattern.AIMED;
            fireCooldown = BEAM_FIRE_INTERVAL;
        } else {
            // Escalating attack phases based on remaining HP.
            double frac = hp / (double) maxHp;
            if (frac > 0.66) {
                pattern = BulletPattern.FAN;
                fireCooldown = 55;
            } else if (frac > 0.33) {
                pattern = BulletPattern.SPIRAL; // rapid rotating spiral
                fireCooldown = 7;
            } else {
                pattern = BulletPattern.RING;
                fireCooldown = 40;
            }
        }
        return volley(pattern, playerX, playerY);
    }

    /**
     * Wave gauntlet, with a light bullet trickle only in the gap between
     * volleys — the walls are the threat, and stacking a pattern on top of a
     * lane that may be down to ~130px would stop being dodgeable.
     */
    private List<Bullet> fireConstrict(int playerX, int playerY) {
        if (--waveTimer <= 0) {
            waveTimer = WAVE_INTERVAL;
            waveCrossing = WAVE_CROSSING;
            triggerWeaponAnim();
            return fireWaveVolley();
        }
        if (waveCrossing > 0) {
            return Collections.emptyList(); // a volley is still crossing
        }
        if (--fireCooldown > 0) {
            return Collections.emptyList();
        }
        fireCooldown = WAVE_FIRE_INTERVAL;
        return volley(BulletPattern.FAN, playerX, playerY);
    }

    /**
     * Walls off all but one of the free zone's lanes. The open lane is never the
     * one left open last time, so the player has to keep moving rather than
     * settling into a groove.
     */
    private List<Bullet> fireWaveVolley() {
        int top = getFreeTop();
        int bottom = getFreeBottom();
        double slot = (bottom - top) / (double) WAVE_SLOTS;
        int open = beamRng.nextInt(WAVE_SLOTS);
        if (open == lastOpenSlot) {
            open = (open + 1 + beamRng.nextInt(WAVE_SLOTS - 1)) % WAVE_SLOTS;
        }
        lastOpenSlot = open;

        int height = (int) Math.round(slot);
        List<Bullet> out = new ArrayList<>();
        for (int s = 0; s < WAVE_SLOTS; s++) {
            if (s == open) {
                continue;
            }
            double cy = top + slot * (s + 0.5);
            out.add(new WaveShot(faction, x, cy, WAVE_SPEED, height));
        }
        return out;
    }

    private List<Bullet> volley(BulletPattern pattern, int playerX, int playerY) {
        int cx = x + getImage().getWidth(null) / 2;
        int cy = y + getImage().getHeight(null) / 2;
        List<Bullet> out = pattern.fire(cx, cy, playerX, playerY, shotCount, ammo);
        shotCount++;
        triggerWeaponAnim();
        return out;
    }
}
