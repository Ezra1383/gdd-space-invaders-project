package gdd.scene;

import gdd.AudioPlayer;
import gdd.Background;
import gdd.Game;
import static gdd.Global.*;
import gdd.Director;
import gdd.Faction;
import gdd.GifSprites;
import gdd.Images;
import gdd.Sfx;
import gdd.SpawnDetails;
import gdd.SpawnSource;
import gdd.Weapons;
import gdd.powerup.PowerUp;
import gdd.powerup.SpeedUp;
import gdd.powerup.WeaponUp;
import gdd.sprite.Boss;
import gdd.sprite.Bullet;
import gdd.sprite.Destruction;
import gdd.sprite.Enemy;
import gdd.sprite.EnemyType;
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

    private boolean firing = false;
    private int fireTimer = 0;

    // Boss fight (Stage 7).
    private static final int BOSS_HP = 120;
    private Boss activeBoss;
    private int bossBannerTimer = 0;
    private int bossesBeaten = 0;

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
    // Fixed seed so runs are reproducible while tuning. Swap to `new Random()`
    // for a fresh, varied run each launch.
    private static final long RUN_SEED = 20260719L;
    private final Random randomizer = new Random(RUN_SEED);
    // Independent stream for kill-drop rolls, so drops don't perturb the
    // Director's wave generation.
    private final Random dropRng = new Random(RUN_SEED + 1337);
    // Likewise for boss ray placement — gameplay, so seeded and reproducible,
    // but on its own stream so it can't shift wave generation.
    private final Random bossRng = new Random(RUN_SEED + 991);
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

            if (enemy.isDying()) {

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
            g.drawString("WEAPON: Lv " + player.getWeaponLevel() + "  SPEED: " + player.getSpeed(), 10, 38);
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
            drawBeam(g);
            drawPlayer(g);
            drawShot(g);
            drawBoss(g);

            g.translate(-ox, -oy);

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
        if (firing && fireTimer <= 0 && inGame) {
            fireWeapon();
            fireTimer = FIRE_INTERVAL;
        }

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
                player.setImage(Images.load(IMG_EXPLOSION));
                player.setDying(true);
                bullet.die();
                Sfx.playerDeath();
                shake(30, 12);
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

                        if (enemy.hit()) { // reduce HP; true when it dies
                            int enemyX = enemy.getX() + enemy.getImage().getWidth(null) / 2;
                            int enemyY = enemy.getY() + enemy.getImage().getHeight(null) / 2;

                            enemy.setDying(true);
                            // That ship's own destruction animation, centred on it.
                            explosions.add(new Destruction(enemy.getFaction(),
                                    enemy.getShipName(), enemyX, enemyY,
                                    enemy.getSpriteSize() + 24));
                            Sfx.enemyExplode();
                            deaths++;

                            // Random powerup drop on kill — the only way to get
                            // powerups now. Mixed: mostly weapon-ups, some speed.
                            // Drifts left toward the player.
                            if (dropRng.nextInt(100) < POWERUP_DROP_PERCENT) {
                                if (dropRng.nextInt(100) < 55) {
                                    powerups.add(new WeaponUp(enemyX, enemyY));
                                } else {
                                    powerups.add(new SpeedUp(enemyX, enemyY));
                                }
                            }
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
                player.setImage(Images.load(IMG_EXPLOSION));
                player.setDying(true);
                Sfx.playerDeath();
                shake(30, 12);
            }
        }

        // Boss lifecycle: count down the intro banner, and on boss death drop a
        // big reward (several powerups) plus extra explosions.
        if (bossBannerTimer > 0) {
            bossBannerTimer--;
        }
        if (activeBoss != null && activeBoss.isDying()) {
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
            Boss boss = new Boss(faction, name, sd.x, sd.y, BOSS_HP, bossRng);
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
            // on its own. V summons it for play-testing.
            if (key == KeyEvent.VK_V && activeBoss == null) {
                spawn(new SpawnDetails(frame, "BOSS:VOID:NEMESIS",
                        BOARD_WIDTH, BOARD_HEIGHT / 2));
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
            default: // tier 5: big plasma beam
                shots.add(new Shot(px, py, SHOT_SPEED + 3, 0, BULLET_COMET));
                break;
        }
    }
}
