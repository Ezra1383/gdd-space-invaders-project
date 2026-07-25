package gdd.scene;

import gdd.AudioPlayer;
import gdd.Background;
import gdd.Director;
import gdd.Faction;
import gdd.Game;
import gdd.GifSprites;
import static gdd.Global.*;
import gdd.Images;
import gdd.RealityBreak;
import gdd.Sfx;
import gdd.SpawnDetails;
import gdd.SpawnSource;
import gdd.Weapons;
import gdd.powerup.Clone;
import gdd.powerup.Corruption;
import gdd.powerup.PowerUp;
import gdd.powerup.SpeedUp;
import gdd.powerup.WeaponUp;
import gdd.sprite.BlueSelf;
import gdd.sprite.Boss;
import gdd.sprite.Bullet;
import gdd.sprite.Destruction;
import gdd.sprite.Enemy;
import gdd.sprite.EnemyType;
import gdd.sprite.SheetBlast;
import gdd.sprite.Player;
import gdd.sprite.Shot;
import gdd.sprite.Sprite;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;

public class Scene1 extends JPanel {

    private int frame = 0;
    private List<PowerUp> powerups;
    private List<Enemy> enemies;
    private List<Sprite> explosions; // animated Destruction effects
    private List<Shot> shots;
    private List<Bullet> enemyBullets;
    private Player player;
    // private Shot shot;

    // Weapon-tier bullet sprites now live in gdd.Weapons — Nemesis fires them
    // back at us, so both sides share one definition.
    private static final Image BULLET_PELLET = Weapons.PELLET;
    private static final Image BULLET_ORB = Weapons.ORB;
    private static final Image BULLET_COMET = Weapons.COMET;
    private static final double SHOT_SPEED = Weapons.SHOT_SPEED;
    private static final int FIRE_INTERVAL = 9; // frames between volleys while firing
    // The tier-5 comet fires alone, so it hits for this much — makes the top
    // tier the strongest DPS rather than the weakest.
    private static final int COMET_DAMAGE = 4;

    private boolean firing = false;
    private int fireTimer = 0;

    // Boss fight (Stage 7). BOSS_HP is the Nairan/Kla'ed bosses; Nemesis, the
    // final boss, has its own so tuning it doesn't touch the others.
    private static final int BOSS_HP = 120;
    private static final int NEMESIS_HP = 160; // the final boss — the tankiest fight
    private Boss activeBoss;
    private int bossBannerTimer = 0;
    private int bossesBeaten = 0;

    // Nemesis's final phase tears extra windows open at the screen edges. Bonus
    // spectacle: the in-board attacks stay fully dodgeable without them, so this
    // can be switched off with no effect on fairness.
    private static final boolean REALITY_BREAK_ENABLED = true;
    private RealityBreak realityBreak;

    // Nemesis's death is a set-piece, not an instant vanish: the ship is torn
    // apart over this many frames while the tears snap shut, ending in a flash.
    private static final int NEMESIS_DEATH_FRAMES = 140;
    private int nemesisDeathTimer = 0;
    private int flash = 0; // white climax flash, counts down in the draw

    // Corruption: after Nemesis dies the run goes on, but kills start dropping
    // shards that turn the player into Nemesis (redder + a forward Ray).
    private boolean nemesisDefeated = false;
    private static final int CORRUPTION_DROP_PERCENT = 45; // of drops, once unlocked
    // The player's own Ray, earned through corruption: a piercing beam fired
    // right, that damages every enemy in its lane. Stronger each stage.
    private static final int PLAYER_RAY_FIRE_FRAMES = 12;
    private int playerRayTimer = 0;   // frames until the next beam
    private int playerRayActive = 0;  // frames the beam stays lethal

    // Final loop: once fully corrupt, the roles swap and you fight your blue
    // past self. You take the boss's right-side arena and its whole kit; the
    // blue self is invincible and grinds down YOUR health bar — a fight you can
    // only ever lose, sealing the loop.
    private boolean finalLoop = false;
    private BlueSelf blueSelf;
    private List<Bullet> blueBullets;
    private RealityBreak finalWindows;
    private static final int FINAL_HEALTH = 60;
    private int finalHealth = FINAL_HEALTH;
    private int finalBannerTimer = 0;
    // Your four-window Ray, aimed into the board at the blue self. The tears
    // charge (telegraph) then fire an in-board crosshair band on its column/row.
    private static final int WIN_IDLE = 96;
    private static final int WIN_CHARGE = 55;
    private static final int WIN_FIRE = 60;
    private static final int WIN_RAY_HALF = 22;
    private int winRayTimer = 0;
    private int winRayPhase = 0; // 0 idle, 1 charging, 2 firing
    private int winRayX = BOARD_WIDTH / 4;  // vertical band column (top/bottom tears)
    private int winRayY = BOARD_HEIGHT / 2;  // horizontal band row (left/right tears)

    // Screen shake (Stage 9). Purely cosmetic, so it uses its own unseeded RNG
    // and never touches the Director's reproducible stream.
    private final Random shakeRng = new Random();
    private int shakeTimer = 0;
    private int shakeFrames = 1;
    private int shakeMag = 0;

    private int deaths = 0;

    private boolean inGame = true;
    private String message = "Game Over";

    private final Dimension d = new Dimension(BOARD_WIDTH, BOARD_HEIGHT);
    // Each launch is a fresh, varied run. Flip FIXED_SEED to true (with the seed
    // below) when you need a reproducible run for tuning or bug-hunting.
    private static final boolean FIXED_SEED = false;
    private static final long RUN_SEED = 20260719L;
    private final long runSeed = FIXED_SEED ? RUN_SEED : System.nanoTime();
    private final Random randomizer = new Random(runSeed);
    // Independent stream for kill-drop rolls, so drops don't perturb the
    // Director's wave generation.
    private final Random dropRng = new Random(runSeed + 1337);
    // Likewise for boss ray placement — gameplay, but on its own stream so it
    // can't shift wave generation.
    private final Random bossRng = new Random(runSeed + 991);
    private static final int POWERUP_DROP_PERCENT = 20;

    private Timer timer;
    private final Game game;

    private SpawnSource spawnSource;
    private AudioPlayer audioPlayer;

    // Parallax backdrop. Swapped when the Director moves the run into a new
    // biome, so the sky changes with the roster rather than on its own timer.
    private Background backdrop;
    private Faction backdropBiome;
    /**
     * Art-review aid: B cycles the backdrop through every biome, N returns it to
     * following the run. Biome 3 has no enemy roster yet, so this is the only
     * way to see its sky.
     */
    private Faction backdropOverride;

    public Scene1(Game game) {
        this.game = game;
        // initBoard();
        // gameInit();
        loadSpawnDetails();
    }

    private void initAudio() {
        try {
            String filePath = "src/audio/scene1.wav";
            audioPlayer = new AudioPlayer(filePath);
            audioPlayer.play();
        } catch (Exception e) {
            System.err.println("Error initializing audio player: " + e.getMessage());
        }
    }

    private void loadSpawnDetails() {
        // Stage 4: spawns now come from the runtime Director (seeded, budget- and
        // phase-driven) instead of a fixed list. StaticSpawnSource still exists
        // for scripted sequences (e.g. a hand-authored intro) if wanted later.
        spawnSource = new Director(randomizer);
    }

    private void initBoard() {

    }

    public void start() {
        addKeyListener(new TAdapter());
        setFocusable(true);
        requestFocusInWindow();
        setBackground(Color.black);

        timer = new Timer(1000 / 60, new GameCycle());
        timer.start();

        gameInit();
        initAudio();
        Sfx.init(); // warm up sound effects so the first shot doesn't hitch
    }

    public void stop() {
        timer.stop();
        if (realityBreak != null) {
            realityBreak.close();
            realityBreak = null;
        }
        if (finalWindows != null) {
            finalWindows.close();
            finalWindows = null;
        }
        try {
            if (audioPlayer != null) {
                audioPlayer.stop();
            }
        } catch (Exception e) {
            System.err.println("Error closing audio player.");
        }
    }

    private void gameInit() {

        enemies = new ArrayList<>();
        powerups = new ArrayList<>();
        explosions = new ArrayList<>();
        shots = new ArrayList<>();
        enemyBullets = new ArrayList<>();

        // for (int i = 0; i < 4; i++) {
        // for (int j = 0; j < 6; j++) {
        // var enemy = new Enemy(ALIEN_INIT_X + (ALIEN_WIDTH + ALIEN_GAP) * j,
        // ALIEN_INIT_Y + (ALIEN_HEIGHT + ALIEN_GAP) * i);
        // enemies.add(enemy);
        // }
        // }
        player = new Player();

        // Built here (not lazily in update) so the first paint has a backdrop.
        backdropBiome = spawnSource.biome();
        backdrop = Background.of(backdropBiome);
    }

    /** Kicks off a screen shake that decays over `frames`. */
    private void shake(int frames, int magnitude) {
        shakeTimer = frames;
        shakeFrames = Math.max(1, frames);
        shakeMag = magnitude;
    }

    // --- Nemesis death set-piece -----------------------------------------

    private void beginNemesisDeath() {
        nemesisDeathTimer = NEMESIS_DEATH_FRAMES;
        player.setDuelZone(false);        // the arena is ours again
        if (realityBreak != null) {
            realityBreak.collapse();      // reality reknits as it dies
        }
        Sfx.enemyExplode();
        shake(22, 7);
    }

    /**
     * Advances the finale one frame: blasts walk across the frozen hull, faster
     * and harder as it goes, the tears retract, and the last frame detonates.
     */
    private void advanceNemesisDeath() {
        nemesisDeathTimer--;
        if (realityBreak != null) {
            realityBreak.tickCollapse();
            if (realityBreak.isClosed()) {
                realityBreak = null;
            }
        }

        int cx = activeBoss.getX() + activeBoss.getImage().getWidth(null) / 2;
        int cy = activeBoss.getY() + activeBoss.getImage().getHeight(null) / 2;
        int rw = activeBoss.getImage().getWidth(null);
        int rh = activeBoss.getImage().getHeight(null);

        double progress = 1.0 - nemesisDeathTimer / (double) NEMESIS_DEATH_FRAMES;
        int interval = Math.max(4, 12 - (int) (9 * progress));
        if (nemesisDeathTimer % interval == 0) {
            addBlast(cx + shakeRng.nextInt(rw + 1) - rw / 2,
                    cy + shakeRng.nextInt(rh + 1) - rh / 2, 3);
            Sfx.enemyExplode();
            shake(8, 4 + (int) (9 * progress));
        }

        if (nemesisDeathTimer <= 0) {
            finishNemesisDeath(cx, cy);
        }
    }

    private void finishNemesisDeath(int cx, int cy) {
        for (int i = 0; i < 6; i++) {
            addBlast(cx + shakeRng.nextInt(161) - 80, cy + shakeRng.nextInt(161) - 80, 5);
        }
        flash = 14;
        shake(52, 20);
        Sfx.bossDeath();
        enemyBullets.clear();             // reality reknits — the shots wink out
        if (realityBreak != null) {
            realityBreak.close();
            realityBreak = null;
        }
        int bx = activeBoss.getX() + activeBoss.getImage().getWidth(null) / 2;
        int by = activeBoss.getY() + activeBoss.getImage().getHeight(null) / 2;
        powerups.add(new WeaponUp(bx, by - 40));
        powerups.add(new SpeedUp(bx, by + 40));
        activeBoss.die();                 // hide it; the cull removes it next frame
        activeBoss = null;
        bossesBeaten++;
        nemesisDeathTimer = 0;
        nemesisDefeated = true;           // corruption shards can now drop
    }

    /** A Nemesis-red explosion from the sprite sheet, centred on x,y. */
    private void addBlast(int x, int y, int scale) {
        explosions.add(new SheetBlast(x, y, true, scale));
    }

    /**
     * Destroys an enemy: its wreck animation, sound, score, and a random drop.
     * Shared by shot hits and the corrupted player Ray. Once Nemesis has fallen,
     * a share of drops become corruption shards — the path to becoming Nemesis.
     */
    private void killEnemy(Enemy enemy) {
        int ex = enemy.getX() + enemy.getImage().getWidth(null) / 2;
        int ey = enemy.getY() + enemy.getImage().getHeight(null) / 2;
        enemy.setDying(true);
        explosions.add(new Destruction(enemy.getFaction(), enemy.getShipName(), ex, ey,
                enemy.getSpriteSize() + 24));
        Sfx.enemyExplode();
        deaths++;

        if (dropRng.nextInt(100) < POWERUP_DROP_PERCENT) {
            int roll = dropRng.nextInt(100);
            if (nemesisDefeated && !player.isFullyCorrupt() && roll < CORRUPTION_DROP_PERCENT) {
                powerups.add(new Corruption(ex, ey));
            } else if (roll < 45) {
                powerups.add(new WeaponUp(ex, ey));
            } else if (roll < 75) {
                powerups.add(new SpeedUp(ex, ey));
            } else {
                powerups.add(new Clone(ex, ey)); // ~25% of drops: extra ship
            }
        }
    }

    /** Kills the player: its own blue sheet burst, plus sound and shake. */
    private void killPlayer() {
        if (player.isDying()) {
            return;
        }
        // A clone soaks the hit before the player can die: it bursts where the
        // clone was, and the player fights on with one fewer.
        if (player.absorbWithClone()) {
            int[] off = player.cloneOffset(player.getClones()); // the one just spent
            int cx = player.getX() + off[0] + player.getImage().getWidth(null) / 2;
            int cy = player.getY() + off[1] + player.getImage().getHeight(null) / 2;
            explosions.add(new SheetBlast(cx, cy, false, 3));
            Sfx.playerDeath();
            shake(14, 6);
            return;
        }
        int pcx = player.getX() + player.getImage().getWidth(null) / 2;
        int pcy = player.getY() + player.getImage().getHeight(null) / 2;
        explosions.add(new SheetBlast(pcx, pcy, false, 3)); // blue = the player
        player.setImage(Images.load(IMG_EXPLOSION));
        player.setDying(true);
        Sfx.playerDeath();
        shake(30, 12);
    }

    // --- corrupted player Ray --------------------------------------------

    /** Half-thickness of the player's Ray band; wider the more corrupt you are. */
    private static int rayHalf(int corruption) {
        return 8 + corruption * 4; // 12 .. 28
    }

    /** Frames between the player's Ray shots; shorter the more corrupt you are. */
    private static int rayCooldown(int corruption) {
        return Math.max(30, 96 - corruption * 12); // 84 .. 36
    }

    /**
     * Fires and sustains the player's forward Ray. While the beam is live, every
     * enemy in its lane to the player's right takes damage each frame — the same
     * signature attack Nemesis used, now turned on the horde.
     */
    private void updatePlayerRay() {
        int c = player.getCorruption();
        if (c <= 0 || !inGame || player.isDying()) {
            playerRayActive = 0;
            return;
        }
        if (playerRayActive > 0) {
            playerRayActive--;
            int cy = player.getY() + player.getImage().getHeight(null) / 2;
            int half = rayHalf(c);
            if (finalLoop) {
                // The beam fires LEFT at the blue past self — which only flashes,
                // never dies. You can hit it; you can never beat it.
                if (blueSelf != null && blueSelf.isVisible()) {
                    int top = blueSelf.getY();
                    int bottom = top + blueSelf.getImage().getHeight(null);
                    if (bottom > cy - half && top < cy + half
                            && blueSelf.getX() < player.getX()) {
                        blueSelf.hit();
                    }
                }
            } else {
                int px = player.getX() + player.getImage().getWidth(null);
                for (Enemy e : enemies) {
                    if (!e.isVisible() || e.isDying()) {
                        continue;
                    }
                    int top = e.getY();
                    int bottom = top + e.getImage().getHeight(null);
                    int right = e.getX() + e.getImage().getWidth(null);
                    if (bottom > cy - half && top < cy + half && right > px && e.hit(1)) {
                        killEnemy(e); // does not touch the enemies list, safe mid-loop
                    }
                }
            }
        } else if (--playerRayTimer <= 0) {
            playerRayActive = PLAYER_RAY_FIRE_FRAMES;
            playerRayTimer = rayCooldown(c);
            Sfx.shoot();
        }
    }

    private void drawPlayerRay(Graphics g) {
        if (playerRayActive <= 0 || player.getCorruption() <= 0 || !player.isVisible()) {
            return;
        }
        int half = rayHalf(player.getCorruption());
        int cy = player.getY() + player.getImage().getHeight(null) / 2;
        // Fires left in the final loop (you're on the right now), else right.
        int x0 = finalLoop ? 0 : player.getX() + player.getImage().getWidth(null);
        int x1 = finalLoop ? player.getX() : BOARD_WIDTH;
        Graphics2D g2 = (Graphics2D) g;
        Composite oldc = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2.setColor(new Color(255, 70, 90)); // outer red glow
        g2.fillRect(x0, cy - half, x1 - x0, half * 2);
        g2.setComposite(oldc);
        g2.setColor(new Color(255, 220, 230)); // bright core
        g2.fillRect(x0, cy - half / 3, x1 - x0, (half / 3) * 2);
    }

    // --- final loop: fighting your blue past self ------------------------

    private void beginFinalLoop() {
        finalLoop = true;
        finalHealth = FINAL_HEALTH;
        finalBannerTimer = 220;
        winRayPhase = 0;
        winRayTimer = 0;
        fireTimer = 0;
        enemies.clear();
        enemyBullets.clear();
        shots.clear();
        powerups.clear();
        activeBoss = null;
        if (realityBreak != null) {
            realityBreak.close();
            realityBreak = null;
        }
        player.enterFinalArena();       // take the boss's right-side ground
        blueSelf = new BlueSelf(60, BOARD_HEIGHT / 2 - 20);
        blueBullets = new ArrayList<>();
        finalWindows = new RealityBreak();
        finalWindows.open(this);        // the four tears, now YOUR attack
        message = "THE LOOP IS COMPLETE";
        Sfx.bossWarn();
        shake(30, 12);
        System.out.println(">>> FINAL LOOP: you are Nemesis now — and you cannot win.");
    }

    private void updateFinalLoop() {
        if (finalBannerTimer > 0) {
            finalBannerTimer--;
        }
        if (player.isDying()) {
            return;
        }
        int pcx = player.getX() + player.getImage().getWidth(null) / 2;
        int pcy = player.getY() + player.getImage().getHeight(null) / 2;

        // Your normal bullets, still fired (left) on top of the Ray — the tier-4
        // spread and the tier-5 comet together, the way Nemesis fought you.
        if (fireTimer > 0) {
            fireTimer--;
        }
        if (firing && fireTimer <= 0) {
            fireCorrupted();
            fireTimer = FIRE_INTERVAL;
        }
        List<Shot> shotGone = new ArrayList<>();
        for (Shot s : shots) {
            s.act();
            if (blueSelf != null && blueSelf.isVisible() && s.isVisible()
                    && s.collidesWith(blueSelf)) {
                blueSelf.hit();
                s.die();
            }
            if (s.getX() < -40 || s.getX() > BOARD_WIDTH
                    || s.getY() < -40 || s.getY() > BOARD_HEIGHT + 40) {
                s.die();
            }
            if (!s.isVisible()) {
                shotGone.add(s);
            }
        }
        shots.removeAll(shotGone);

        blueSelf.act(player.getY());
        blueBullets.addAll(blueSelf.maybeFire(pcx, pcy));

        List<Bullet> gone = new ArrayList<>();
        for (Bullet b : blueBullets) {
            b.act();
            if (player.isVisible() && b.collidesWith(player)) {
                finalHealth--;
                b.die();
                if (finalHealth % 8 == 0) {
                    shake(5, 3);
                }
            }
            int bw = b.getImage().getWidth(null);
            int bh = b.getImage().getHeight(null);
            if (b.getX() < -bw || b.getX() > BOARD_WIDTH
                    || b.getY() < -bh || b.getY() > BOARD_HEIGHT) {
                b.die();
            }
            if (!b.isVisible()) {
                gone.add(b);
            }
        }
        blueBullets.removeAll(gone);

        updateWindowRays();

        if (finalHealth <= 0) {
            endFinalLoop();
        }
    }

    /**
     * Cycles the four-window Ray: idle → charge (telegraph) → fire. The tears
     * and the in-board bands are driven together. When it fires, the crosshair
     * band flashes the blue self it's caught (which, of course, never dies).
     */
    private void updateWindowRays() {
        winRayTimer++;
        if (winRayPhase == 0 && winRayTimer >= WIN_IDLE) {
            winRayPhase = 1;
            winRayTimer = 0;
            if (blueSelf != null) { // aim the crosshair where the blue self is
                winRayX = blueSelf.getX() + blueSelf.getImage().getWidth(null) / 2;
                winRayY = blueSelf.getY() + blueSelf.getImage().getHeight(null) / 2;
            }
        } else if (winRayPhase == 1 && winRayTimer >= WIN_CHARGE) {
            winRayPhase = 2;
            winRayTimer = 0;
        } else if (winRayPhase == 2 && winRayTimer >= WIN_FIRE) {
            winRayPhase = 0;
            winRayTimer = 0;
        }
        if (finalWindows != null) {
            finalWindows.setShowState(winRayPhase == 1, winRayPhase == 2);
        }
        if (winRayPhase == 2 && blueSelf != null && blueSelf.isVisible()) {
            int bx = blueSelf.getX() + blueSelf.getImage().getWidth(null) / 2;
            int by = blueSelf.getY() + blueSelf.getImage().getHeight(null) / 2;
            if (Math.abs(bx - winRayX) < WIN_RAY_HALF || Math.abs(by - winRayY) < WIN_RAY_HALF) {
                blueSelf.hit();
            }
        }
    }

    /**
     * Your corrupted fire: the tier-4 three-way spread and the tier-5 comet at
     * once (the player's own blue weapons), sent left at the blue self.
     */
    private void fireCorrupted() {
        Sfx.shoot();
        int px = player.getX();                                  // left edge
        int py = player.getY() + player.getImage().getHeight(null) / 2;
        double s = -SHOT_SPEED;                                  // travels left
        shots.add(new Shot(px, py, s, 0, BULLET_ORB));
        shots.add(new Shot(px, py, s, -3, BULLET_ORB));
        shots.add(new Shot(px, py, s, 3, BULLET_ORB));
        shots.add(new Shot(px, py, s - 3, 0, BULLET_COMET, COMET_DAMAGE));
    }

    /** You fall. The loop closes — the villain always loses to the hero. */
    private void endFinalLoop() {
        int pcx = player.getX() + player.getImage().getWidth(null) / 2;
        int pcy = player.getY() + player.getImage().getHeight(null) / 2;
        explosions.add(new SheetBlast(pcx, pcy, true, 5)); // you die red
        player.setImage(Images.load(IMG_EXPLOSION));
        player.setDying(true);
        Sfx.playerDeath();
        shake(48, 16);
        if (finalWindows != null) {
            finalWindows.close();
            finalWindows = null;
        }
        message = "THE LOOP IS COMPLETE";
    }

    private void drawFinalLoop(Graphics g) {
        if (!finalLoop) {
            return;
        }
        drawWindowRays(g);
        for (Bullet b : blueBullets) {
            if (b.isVisible()) {
                g.drawImage(b.getImage(), b.getX(), b.getY(), this);
            }
        }
        if (blueSelf != null && blueSelf.isVisible()) {
            g.drawImage(blueSelf.getImage(), blueSelf.getX(), blueSelf.getY(), this);
            if (blueSelf.getHitFlash() > 0) {
                Graphics2D g2 = (Graphics2D) g;
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                g2.setColor(Color.WHITE);
                g2.fillRect(blueSelf.getX(), blueSelf.getY(),
                        blueSelf.getImage().getWidth(null), blueSelf.getImage().getHeight(null));
                g2.setComposite(old);
            }
        }
    }

    /** The four-window Ray reaching into the board: a crosshair on the blue self. */
    private void drawWindowRays(Graphics g) {
        if (winRayPhase == 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        if (winRayPhase == 1) { // charging — blinking telegraph
            if ((frame / 4) % 2 == 0) {
                g2.setColor(new Color(120, 160, 255));
                g2.fillRect(winRayX - 2, 0, 4, BOARD_HEIGHT);       // vertical
                g2.fillRect(0, winRayY - 2, BOARD_WIDTH, 4);        // horizontal
            }
            return;
        }
        // firing — full crosshair bands from all four tears
        int half = WIN_RAY_HALF;
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g2.setColor(new Color(120, 170, 255));
        g2.fillRect(winRayX - half, 0, half * 2, BOARD_HEIGHT);
        g2.fillRect(0, winRayY - half, BOARD_WIDTH, half * 2);
        g2.setComposite(old);
        g2.setColor(new Color(235, 245, 255));
        g2.fillRect(winRayX - half / 3, 0, (half / 3) * 2, BOARD_HEIGHT);
        g2.fillRect(0, winRayY - half / 3, BOARD_WIDTH, (half / 3) * 2);
    }

    /** Your own boss-style health bar — the fight is now about your survival. */
    private void drawFinalHealthBar(Graphics g) {
        int barW = BOARD_WIDTH - 120;
        int barH = 12;
        int bx = 60;
        int by = 54;
        double frac = Math.max(0, finalHealth / (double) FINAL_HEALTH);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(bx, by, barW, barH);
        g.setColor(new Color(210, 40, 40));
        g.fillRect(bx, by, (int) (barW * frac), barH);
        g.setColor(Color.WHITE);
        g.drawRect(bx, by, barW, barH);
        g.drawString("NEMESIS (you)", bx, by - 4);
        if (finalBannerTimer > 0) {
            var f = new Font("Helvetica", Font.BOLD, 22);
            g.setFont(f);
            g.setColor(new Color(120, 160, 255));
            String msg = "FIGHT YOUR PAST SELF — YOU CANNOT WIN";
            g.drawString(msg, (BOARD_WIDTH - getFontMetrics(f).stringWidth(msg)) / 2, 110);
        }
    }

    // Resets the whole run after a Game Over so the player can play again.
    private void restart() {
        frame = 0;
        deaths = 0;
        bossesBeaten = 0;
        activeBoss = null;
        bossBannerTimer = 0;
        shakeTimer = 0;
        firing = false;
        fireTimer = 0;
        message = "Game Over";
        inGame = true;
        nemesisDeathTimer = 0;
        flash = 0;
        nemesisDefeated = false;
        playerRayTimer = 0;
        playerRayActive = 0;
        finalLoop = false;
        finalHealth = FINAL_HEALTH;
        finalBannerTimer = 0;
        winRayPhase = 0;
        winRayTimer = 0;
        blueSelf = null;
        blueBullets = null;
        if (finalWindows != null) {
            finalWindows.close();
            finalWindows = null;
        }
        if (realityBreak != null) {
            realityBreak.close();
            realityBreak = null;
        }
        loadSpawnDetails(); // fresh Director
        gameInit();         // fresh player + entity lists
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void drawAliens(Graphics g) {

        for (Enemy enemy : enemies) {

            if (enemy.isVisible()) {

                g.drawImage(enemy.getImage(), enemy.getX(), enemy.getY(), this);

                // Weapon flash is cropped with the same box as the ship, so it
                // lines up exactly at the ship's own position.
                var weapon = enemy.getWeaponOverlay();
                if (weapon != null) {
                    g.drawImage(weapon, enemy.getX(), enemy.getY(), this);
                }

                // Shield bubble extends past the hull, so it's drawn centred.
                var shield = enemy.getShieldOverlay();
                if (shield != null) {
                    int cx = enemy.getX() + enemy.getImage().getWidth(null) / 2;
                    int cy = enemy.getY() + enemy.getImage().getHeight(null) / 2;
                    g.drawImage(shield, cx - shield.getWidth() / 2,
                            cy - shield.getHeight() / 2, this);
                }
            }

            // Dying enemies hide themselves next frame — except Nemesis, which
            // stays on screen (frozen) so its death set-piece can tear it apart.
            if (enemy.isDying() && !(enemy == activeBoss && nemesisDeathTimer > 0)) {
                enemy.die();
            }
        }
    }

    private void drawPowreUps(Graphics g) {

        for (PowerUp p : powerups) {

            if (p.isVisible()) {

                g.drawImage(p.getImage(), p.getX(), p.getY(), this);
            }

            if (p.isDying()) {

                p.die();
            }
        }
    }

    private void drawPlayer(Graphics g) {

        if (player.isVisible()) {

            // Clones: 60%-transparent copies of the ship, drawn behind it. Uses
            // the player's live image, so they bank and redden along with it.
            if (!player.isDying() && player.getClones() > 0) {
                Graphics2D g2 = (Graphics2D) g;
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                for (int i = 0; i < player.getClones(); i++) {
                    int[] off = player.cloneOffset(i);
                    g2.drawImage(player.getImage(),
                            player.getX() + off[0], player.getY() + off[1], this);
                }
                g2.setComposite(old);
            }

            g.drawImage(player.getImage(), player.getX(), player.getY(), this);
        }

        if (player.isDying()) {

            player.die();
            inGame = false;
        }
    }

    private void drawShot(Graphics g) {

        for (Shot shot : shots) {

            if (shot.isVisible()) {
                g.drawImage(shot.getImage(), shot.getX(), shot.getY(), this);
            }
        }
    }

    private void drawEnemyBullets(Graphics g) {

        for (Bullet bullet : enemyBullets) {

            if (bullet.isVisible()) {
                g.drawImage(bullet.getImage(), bullet.getX(), bullet.getY(), this);
            }
        }
    }

    /**
     * The boss's signature Ray: telegraphed lines, then arena-wide killing
     * bands. They span the full board and are anchored at the right edge rather
     * than the boss's hull, because their placement is independent of where the
     * boss happens to be patrolling.
     */
    private void drawBeam(Graphics g) {
        if (activeBoss == null || !activeBoss.isVisible()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        Composite old = g2.getComposite();

        // Permanent rays (Kla'ed): walls that have locked in for the rest of the
        // fight. Drawn in the faction's red so they never read as the temporary
        // blue sweep — one is a hazard that passes, the other never leaves.
        int[] perm = activeBoss.getPermanentRays();
        int armed = activeBoss.getArmedRayCount();
        for (int i = 0; i < perm.length; i++) {
            if (i < armed) {
                drawBand(g2, old, perm[i], new Color(255, 120, 60), new Color(255, 235, 200));
            } else if ((frame / 4) % 2 == 0) {
                // Still arming — a warning only, harmless until it locks in.
                drawWarningLine(g2, perm[i]);
            }
        }

        // Temporary rays (Nairan): telegraphed, then lethal, then gone.
        boolean charging = activeBoss.isBeamCharging();
        boolean firing = activeBoss.isBeamFiring();
        if (charging || firing) {
            for (int cy : activeBoss.getBeamBands()) {
                if (charging) {
                    if ((frame / 4) % 2 == 0) {
                        drawWarningLine(g2, cy);
                    }
                } else {
                    drawBand(g2, old, cy, new Color(120, 170, 255), new Color(235, 245, 255));
                }
            }
        }

        // Nemesis's vertical ray — the other axis giving way.
        int vx = activeBoss.getVerticalRay();
        if (vx != Integer.MIN_VALUE) {
            if (activeBoss.isVerticalCharging()) {
                if ((frame / 4) % 2 == 0) {
                    g2.setColor(new Color(255, 90, 90));
                    g2.fillRect(vx - 2, 0, 4, BOARD_HEIGHT);
                }
            } else if (activeBoss.isVerticalFiring()) {
                int half = Boss.VBEAM_HALF_THICKNESS;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                g2.setColor(new Color(255, 120, 200));
                g2.fillRect(vx - half, 0, half * 2, BOARD_HEIGHT);
                g2.setComposite(old);
                g2.setColor(new Color(255, 235, 250));
                g2.fillRect(vx - half / 3, 0, (half / 3) * 2, BOARD_HEIGHT);
            }
        }
        g2.setComposite(old);
    }

    /** Blinking telegraph so the player can clear the lane before it turns lethal. */
    private void drawWarningLine(Graphics2D g2, int cy) {
        g2.setColor(new Color(255, 90, 90));
        g2.fillRect(0, cy - 2, BOARD_WIDTH, 4);
    }

    /** A live ray: outer glow, bright core, and the pack's Ray art as the emitter. */
    private void drawBand(Graphics2D g2, Composite old, int cy, Color glow, Color core) {
        int half = Boss.BEAM_HALF_THICKNESS;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g2.setColor(glow);
        g2.fillRect(0, cy - half, BOARD_WIDTH, half * 2);
        g2.setComposite(old);
        g2.setColor(core);
        g2.fillRect(0, cy - half / 3, BOARD_WIDTH, (half / 3) * 2);
        // Emitter flare at the edge the ray fires from.
        var flare = GifSprites.beam(activeBoss.getFaction(), Boss.SPRITE_SIZE);
        if (flare.length > 0) {
            var f = flare[(frame / 3) % flare.length];
            g2.drawImage(f, BOARD_WIDTH - f.getWidth(), cy - f.getHeight() / 2, this);
        }
    }

    private void drawBoss(Graphics g) {
        // HP bar while the boss is on the field.
        if (activeBoss != null && activeBoss.isVisible()) {
            int barW = BOARD_WIDTH - 120;
            int barH = 12;
            int bx = 60;
            int by = 54;
            double frac = Math.max(0, activeBoss.getHp() / (double) activeBoss.getMaxHp());
            g.setColor(Color.DARK_GRAY);
            g.fillRect(bx, by, barW, barH);
            g.setColor(Color.RED);
            g.fillRect(bx, by, (int) (barW * frac), barH);
            g.setColor(Color.WHITE);
            g.drawRect(bx, by, barW, barH);
            g.drawString("BOSS: " + activeBoss.getName(), bx, by - 4);
        }
        // Intro warning banner.
        if (bossBannerTimer > 0) {
            var f = new Font("Helvetica", Font.BOLD, 22);
            g.setFont(f);
            g.setColor(Color.RED);
            String msg = "!! WARNING - BOSS APPROACHING !!";
            g.drawString(msg, (BOARD_WIDTH - getFontMetrics(f).stringWidth(msg)) / 2, 110);
        }
    }

    private void drawBombing(Graphics g) {

        // for (Enemy e : enemies) {
        //     Enemy.Bomb b = e.getBomb();
        //     if (!b.isDestroyed()) {
        //         g.drawImage(b.getImage(), b.getX(), b.getY(), this);
        //     }
        // }
    }

    private void drawExplosions(Graphics g) {

        List<Sprite> toRemove = new ArrayList<>();

        for (Sprite explosion : explosions) {

            if (explosion.isVisible()) {
                g.drawImage(explosion.getImage(), explosion.getX(), explosion.getY(), this);
                explosion.visibleCountDown();
                if (!explosion.isVisible()) {
                    toRemove.add(explosion);
                }
            }
        }

        explosions.removeAll(toRemove);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        doDrawing(g);
    }

    private void doDrawing(Graphics g) {

        g.setColor(Color.black);
        g.fillRect(0, 0, d.width, d.height);

        g.setColor(Color.white);
        g.drawString("FRAME: " + frame, 10, 10);
        g.drawString("KILLS: " + deaths, 10, 24);
        if (player != null) {
            String wpn = "WEAPON: Lv " + player.getWeaponLevel()
                    + (player.getWeaponLevel() < Player.MAX_WEAPON
                            ? " (" + player.getWeaponPips() + "/" + player.getWeaponPipsNeeded() + ")"
                            : " MAX");
            String clones = player.getClones() > 0
                    ? "  CLONES: " + player.getClones() + "/" + Player.MAX_CLONES : "";
            g.drawString(wpn + "  SPEED: " + player.getSpeed() + clones, 10, 38);
            if (player.getCorruption() > 0) {
                g.setColor(new Color(255, 90, 110));
                g.drawString("CORRUPTION: " + player.getCorruption() + "/" + Player.MAX_CORRUPTION
                        + (player.isFullyCorrupt() ? "  — YOU ARE NEMESIS" : ""), 10, 52);
            }
        }

        g.setColor(Color.green);

        if (inGame) {

            // Screen shake: offset the whole scene, decaying to zero.
            int ox = 0;
            int oy = 0;
            if (shakeTimer > 0) {
                shakeTimer--;
                int m = Math.max(1, shakeMag * shakeTimer / shakeFrames);
                ox = shakeRng.nextInt(2 * m + 1) - m;
                oy = shakeRng.nextInt(2 * m + 1) - m;
            }
            g.translate(ox, oy);

            backdrop.draw(g); // parallax backdrop, behind everything
            drawExplosions(g);
            drawPowreUps(g);
            drawAliens(g);
            drawEnemyBullets(g);
            drawFinalLoop(g); // the blue past self + its shots
            drawBeam(g);
            drawPlayerRay(g); // the player's corrupted Ray, over the enemies
            drawPlayer(g);
            drawShot(g);
            drawBoss(g);
            if (finalLoop) {
                drawFinalHealthBar(g);
            }

            g.translate(-ox, -oy);

            // Climax flash: a white blaze over everything as Nemesis detonates.
            if (flash > 0) {
                Graphics2D g2 = (Graphics2D) g;
                Composite oldc = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, Math.min(1f, flash / 14f)));
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, BOARD_WIDTH, BOARD_HEIGHT);
                g2.setComposite(oldc);
                flash--;
            }

        } else {

            if (timer.isRunning()) {
                timer.stop();
            }

            gameOver(g);
        }

        Toolkit.getDefaultToolkit().sync();
    }

    private void gameOver(Graphics g) {

        g.setColor(Color.black);
        g.fillRect(0, 0, BOARD_WIDTH, BOARD_HEIGHT);

        int cy = BOARD_HEIGHT / 2;

        // Title
        var big = new Font("Helvetica", Font.BOLD, 40);
        g.setFont(big);
        g.setColor(Color.red);
        drawCentered(g, message, cy - 120, big);

        // Score lines
        var mid = new Font("Helvetica", Font.BOLD, 20);
        g.setFont(mid);
        g.setColor(Color.white);
        drawCentered(g, "Kills: " + deaths, cy - 50, mid);
        drawCentered(g, "Time survived: " + (frame / 60) + "s", cy - 20, mid);
        drawCentered(g, "Bosses beaten: " + bossesBeaten, cy + 10, mid);

        // Prompt (timer is stopped here, so frame is frozen — keep it static).
        var small = new Font("Helvetica", Font.BOLD, 18);
        g.setFont(small);
        g.setColor(Color.yellow);
        drawCentered(g, "Press SPACE to play again", cy + 70, small);
    }

    private void drawCentered(Graphics g, String text, int y, Font font) {
        int w = getFontMetrics(font).stringWidth(text);
        g.drawString(text, (BOARD_WIDTH - w) / 2, y);
    }

    private void update() {


        // Follow the run into a new biome: the backdrop tracks the Director's
        // current phase, so sky and roster always change together. A manual
        // override (B key) lets a biome's art be reviewed without playing to it.
        Faction biome = backdropOverride != null ? backdropOverride : spawnSource.biome();
        if (biome != backdropBiome) {
            backdropBiome = biome;
            backdrop = Background.of(biome);
        }
        backdrop.update();

        // Final loop: your places have swapped and you fight your blue past
        // self. A self-contained mode — none of the normal spawn/enemy logic.
        if (finalLoop) {
            player.act();
            updatePlayerRay();
            updateFinalLoop();
            return;
        }

        // Check spawns for this frame (may be several). The Director gates waves
        // on how many enemies are still alive, so it holds the next wave until
        // the current one is cleared.
        for (SpawnDetails sd : spawnSource.poll(frame, enemies.size())) {
            spawn(sd);
        }

        // The run is now endless (bosses gate the timeline); the old
        // fixed-kill-count win condition no longer applies.

        // player
        player.act();

        // Player weapon fire (held SPACE), paced by a cooldown.
        if (fireTimer > 0) {
            fireTimer--;
        }
        if (firing && fireTimer <= 0 && inGame && !finalLoop) {
            fireWeapon();
            fireTimer = FIRE_INTERVAL;
        }

        // Corrupted player Ray: an auto-firing piercing beam, once the player
        // has taken on any of Nemesis. It gets stronger and more frequent as
        // corruption climbs.
        updatePlayerRay();

        // Power-ups
        for (PowerUp powerup : powerups) {
            if (powerup.isVisible()) {
                powerup.act();
                if (powerup.collidesWith(player)) {
                    powerup.upgrade(player);
                    Sfx.powerup();
                }
            }
        }

        // The transformation is complete — the final loop begins.
        if (player.isFullyCorrupt()) {
            beginFinalLoop();
            return;
        }

        // Enemies — move, and fire danmaku volleys aimed at the player.
        for (Enemy enemy : enemies) {
            if (enemy.isVisible()) {
                enemy.act();
                enemyBullets.addAll(enemy.maybeFire(player.getX(), player.getY()));
            }
        }

        // Enemy bullets — advance, check for a hit on the player, cull off-screen.
        List<Bullet> bulletsToRemove = new ArrayList<>();
        for (Bullet bullet : enemyBullets) {
            bullet.act();

            if (player.isVisible() && bullet.collidesWith(player)) {
                killPlayer();
                bullet.die();
            }

            // Cull by the bullet's own size — projectile sprites vary a lot
            // (a rotated Torpedo canvas is 33px against Bullet.SIZE's 14), so a
            // fixed margin would pop the big ones out while still on screen.
            int bx = bullet.getX();
            int by = bullet.getY();
            int bw = bullet.getImage().getWidth(null);
            int bh = bullet.getImage().getHeight(null);
            if (bx < -bw || bx > BOARD_WIDTH || by < -bh || by > BOARD_HEIGHT) {
                bullet.die();
            }

            if (!bullet.isVisible()) {
                bulletsToRemove.add(bullet);
            }
        }
        enemyBullets.removeAll(bulletsToRemove);

        // shot
        List<Shot> shotsToRemove = new ArrayList<>();
        for (Shot shot : shots) {

            if (shot.isVisible()) {

                for (Enemy enemy : enemies) {
                    // Collision detection: shot and enemy
                    if (enemy.isVisible() && shot.isVisible() && shot.collidesWith(enemy)) {

                        shot.die();
                        shotsToRemove.add(shot);

                        if (enemy.hit(shot.getDamage())) { // reduce HP; true when it dies
                            killEnemy(enemy);
                        }
                    }
                }

                shot.act();

                if (shot.getX() > BOARD_WIDTH || shot.getY() < -20
                        || shot.getY() > BOARD_HEIGHT + 20) {
                    shot.die();
                    shotsToRemove.add(shot);
                }
            }
        }
        shots.removeAll(shotsToRemove);

        // Boss rays: the bands span the whole board, so being caught in any one
        // of them is fatal regardless of how far left the player has run. Rays
        // still arming are excluded — their blink is a warning, not a hazard.
        if (activeBoss != null && player.isVisible()) {
            int half = Boss.BEAM_HALF_THICKNESS;
            int pTop = player.getY();
            int pBottom = pTop + player.getImage().getHeight(null);
            boolean hit = false;
            int[] perm = activeBoss.getPermanentRays();
            for (int i = 0; i < activeBoss.getArmedRayCount() && !hit; i++) {
                hit = pBottom > perm[i] - half && pTop < perm[i] + half;
            }
            if (activeBoss.isBeamFiring()) {
                for (int cy : activeBoss.getBeamBands()) {
                    hit |= pBottom > cy - half && pTop < cy + half;
                }
            }
            // Vertical ray: same test on the other axis.
            if (activeBoss.isVerticalFiring()) {
                int vhalf = Boss.VBEAM_HALF_THICKNESS;
                int vx = activeBoss.getVerticalRay();
                int pLeft = player.getX();
                int pRight = pLeft + player.getImage().getWidth(null);
                hit |= pRight > vx - vhalf && pLeft < vx + vhalf;
            }
            if (hit) {
                killPlayer();
            }
        }

        // Boss lifecycle: count down the intro banner, and on boss death drop a
        // big reward (several powerups) plus extra explosions.
        if (bossBannerTimer > 0) {
            bossBannerTimer--;
        }
        if (activeBoss != null && activeBoss.isDying()
                && activeBoss.getMoveset() != Boss.Moveset.NEMESIS) {
            int bx = activeBoss.getX() + activeBoss.getImage().getWidth(null) / 2;
            int by = activeBoss.getY() + activeBoss.getImage().getHeight(null) / 2;
            powerups.add(new WeaponUp(bx, by - 50));
            powerups.add(new WeaponUp(bx, by + 50));
            powerups.add(new SpeedUp(bx, by));
            // Big flagship wreck, plus two smaller secondary blasts, all in the
            // boss's own faction's art.
            Faction bf = activeBoss.getFaction();
            explosions.add(new Destruction(bf, activeBoss.getShipName(), bx, by,
                    activeBoss.getSpriteSize() + 60));
            explosions.add(new Destruction(bf, "Fighter", bx - 50, by - 35, 70));
            explosions.add(new Destruction(bf, "Fighter", bx + 45, by + 30, 70));
            Sfx.bossDeath();
            shake(35, 14);
            activeBoss = null;
            player.setDuelZone(false);
            bossesBeaten++;
        }

        // Nemesis dies as a set-piece: begin it once, then advance it. The boss
        // has no wreck art (it's the player's own jet, not a ship pack), so the
        // finale is procedural — the hull torn apart while the tears snap shut.
        if (activeBoss != null && activeBoss.isDying()
                && activeBoss.getMoveset() == Boss.Moveset.NEMESIS
                && nemesisDeathTimer == 0) {
            beginNemesisDeath();
        }
        if (nemesisDeathTimer > 0) {
            advanceNemesisDeath();
        }

        // Reality break — Nemesis's final phase. Opened when it drops into that
        // phase, torn down the instant the boss is gone, the player is dying, or
        // the run has ended, so a window can never outlive the fight.
        if (realityBreak != null && (activeBoss == null || !inGame
                || player.isDying() || !activeBoss.isRealityBreaking())) {
            realityBreak.close();
            realityBreak = null;
        }
        if (REALITY_BREAK_ENABLED && inGame && !player.isDying()
                && activeBoss != null && activeBoss.isRealityBreaking()
                && !activeBoss.isDying()) { // the death sequence collapses them
            if (realityBreak == null) {
                realityBreak = new RealityBreak();
                realityBreak.open(this);
                if (realityBreak.isOpen()) {
                    shake(24, 8);
                    Sfx.bossWarn();
                }
            }
            realityBreak.update(activeBoss);
        }

        // Cull dead enemies and any that have drifted off the left edge, so the
        // lists don't grow without bound over an endless run.
        enemies.removeIf(e -> !e.isVisible()
                || e.getX() + e.getImage().getWidth(null) < 0);
        powerups.removeIf(p -> !p.isVisible()
                || p.getX() + p.getImage().getWidth(null) < 0);

        // enemies
        // for (Enemy enemy : enemies) {
        //     int x = enemy.getX();
        //     if (x >= BOARD_WIDTH - BORDER_RIGHT && direction != -1) {
        //         direction = -1;
        //         for (Enemy e2 : enemies) {
        //             e2.setY(e2.getY() + GO_DOWN);
        //         }
        //     }
        //     if (x <= BORDER_LEFT && direction != 1) {
        //         direction = 1;
        //         for (Enemy e : enemies) {
        //             e.setY(e.getY() + GO_DOWN);
        //         }
        //     }
        // }
        // for (Enemy enemy : enemies) {
        //     if (enemy.isVisible()) {
        //         int y = enemy.getY();
        //         if (y > GROUND - ALIEN_HEIGHT) {
        //             inGame = false;
        //             message = "Invasion!";
        //         }
        //         enemy.act(direction);
        //     }
        // }
        // bombs - collision detection
        // Bomb is with enemy, so it loops over enemies
        /*
        for (Enemy enemy : enemies) {

            int chance = randomizer.nextInt(15);
            Enemy.Bomb bomb = enemy.getBomb();

            if (chance == CHANCE && enemy.isVisible() && bomb.isDestroyed()) {

                bomb.setDestroyed(false);
                bomb.setX(enemy.getX());
                bomb.setY(enemy.getY());
            }

            int bombX = bomb.getX();
            int bombY = bomb.getY();
            int playerX = player.getX();
            int playerY = player.getY();

            if (player.isVisible() && !bomb.isDestroyed()
                    && bombX >= (playerX)
                    && bombX <= (playerX + PLAYER_WIDTH)
                    && bombY >= (playerY)
                    && bombY <= (playerY + PLAYER_HEIGHT)) {

                var ii = new ImageIcon(IMG_EXPLOSION);
                player.setImage(ii.getImage());
                player.setDying(true);
                bomb.setDestroyed(true);
            }

            if (!bomb.isDestroyed()) {
                bomb.setY(bomb.getY() + 1);
                if (bomb.getY() >= GROUND - BOMB_HEIGHT) {
                    bomb.setDestroyed(true);
                }
            }
        }
         */
    }

    // Creates a single enemy/powerup from a spawn entry. This is the one place
    // spawn-type logic lives, shared by the static source now and the Stage 4
    // Director later.
    private void spawn(SpawnDetails sd) {
        // Boss gate — spawn a real boss that holds back the next wave until
        // killed. Encoded by the Director as BOSS:<faction>:<name>.
        if (sd.type.startsWith("BOSS:")) {
            String[] parts = sd.type.split(":", 3);
            Faction faction = Faction.valueOf(parts[1]);
            String name = parts[2];
            int hp = faction == Faction.VOID ? NEMESIS_HP : BOSS_HP;
            Boss boss = new Boss(faction, name, sd.x, sd.y, hp, bossRng);
            boss.setHomeX(BOARD_WIDTH - 200);
            enemies.add(boss);
            activeBoss = boss;
            // The mirror duel gives the player the left half instead of the
            // left third — vertical rays need room to dodge sideways.
            player.setDuelZone(boss.getMoveset() == Boss.Moveset.NEMESIS);
            bossBannerTimer = 150;
            Sfx.bossWarn();
            shake(18, 6);
            System.out.println(">>> BOSS INCOMING: " + name);
            return;
        }
        if ("PowerUp-SpeedUp".equals(sd.type)) {
            powerups.add(new SpeedUp(sd.x, sd.y));
            return;
        }
        // Otherwise it's an enemy: the type string names an EnemyType.
        Enemy enemy = new Enemy(EnemyType.fromString(sd.type), sd.x, sd.y);
        enemy.setHomeX(sd.targetX);
        enemies.add(enemy);
    }

    private void doGameCycle() {
        frame++;
        update();
        repaint();
    }

    private class GameCycle implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            doGameCycle();
        }
    }

    private class TAdapter extends KeyAdapter {

        @Override
        public void keyReleased(KeyEvent e) {
            player.keyReleased(e);
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                firing = false;
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();
            if (!inGame) {
                // On the Game Over screen, SPACE restarts the run.
                if (key == KeyEvent.VK_SPACE) {
                    restart();
                }
                return;
            }
            player.keyPressed(e);
            if (key == KeyEvent.VK_SPACE) {
                firing = true; // actual firing cadence is handled in update()
            }
            if (key == KeyEvent.VK_B) {
                Faction[] all = Faction.values();
                int i = backdropOverride == null ? 0 : backdropOverride.ordinal() + 1;
                backdropOverride = all[i % all.length];
                System.out.println("[backdrop] " + backdropOverride);
            }
            if (key == KeyEvent.VK_N) {
                backdropOverride = null;
                System.out.println("[backdrop] following the run");
            }
            // Biome 3 has no enemy roster yet, so the run can't reach Nemesis
            // on its own. V summons it for play-testing — with the endgame
            // loadout the player would actually arrive with (maxed weapon, a few
            // speed drops), so the fight reads at its real difficulty rather
            // than a level-1 one that's ~3x harder than it will ever be.
            if (key == KeyEvent.VK_V && activeBoss == null) {
                player.maxWeapon();
                player.setSpeed(8);
                spawn(new SpawnDetails(frame, "BOSS:VOID:NEMESIS",
                        BOARD_WIDTH, BOARD_HEIGHT / 2));
            }
            // C: grant a corruption stage (play-testing the transformation
            // without first grinding down Nemesis).
            if (key == KeyEvent.VK_C) {
                nemesisDefeated = true;
                player.corrupt();
                System.out.println("[corruption] " + player.getCorruption()
                        + "/" + Player.MAX_CORRUPTION);
            }
        }
    }

    // Fires the current weapon tier: sprite and shot pattern both scale up.
    private void fireWeapon() {
        Sfx.shoot();
        int px = player.getX() + player.getImage().getWidth(null);
        int py = player.getY() + player.getImage().getHeight(null) / 2;
        switch (player.getWeaponLevel()) {
            case 1: // single pellet
                shots.add(new Shot(px, py, SHOT_SPEED, 0, BULLET_PELLET));
                break;
            case 2: // single orb
                shots.add(new Shot(px, py, SHOT_SPEED, 0, BULLET_ORB));
                break;
            case 3: // twin orbs
                shots.add(new Shot(px, py - 9, SHOT_SPEED, 0, BULLET_ORB));
                shots.add(new Shot(px, py + 9, SHOT_SPEED, 0, BULLET_ORB));
                break;
            case 4: // 3-way spread
                shots.add(new Shot(px, py, SHOT_SPEED, 0, BULLET_ORB));
                shots.add(new Shot(px, py, SHOT_SPEED, -3, BULLET_ORB));
                shots.add(new Shot(px, py, SHOT_SPEED, 3, BULLET_ORB));
                break;
            default: // tier 5: big plasma comet — one shot, but it hits hard, so
                     // the top tier is the strongest DPS instead of (as a single
                     // 1-damage slug) the weakest. COMET_DAMAGE is the knob.
                shots.add(new Shot(px, py, SHOT_SPEED + 3, 0, BULLET_COMET, COMET_DAMAGE));
                break;
        }

        // Clones add fire: each lays down a straight orb from its own muzzle.
        for (int i = 0; i < player.getClones(); i++) {
            int[] off = player.cloneOffset(i);
            shots.add(new Shot(px + off[0], py + off[1], SHOT_SPEED, 0, BULLET_ORB));
        }
    }
}
