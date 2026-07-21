package gdd.scene;

import gdd.AudioPlayer;
import gdd.Game;
import static gdd.Global.*;
import gdd.Director;
import gdd.Images;
import gdd.SpawnDetails;
import gdd.SpawnSource;
import gdd.powerup.PowerUp;
import gdd.powerup.SpeedUp;
import gdd.powerup.WeaponUp;
import gdd.sprite.Boss;
import gdd.sprite.Bullet;
import gdd.sprite.Enemy;
import gdd.sprite.EnemyType;
import gdd.sprite.Explosion;
import gdd.sprite.Player;
import gdd.sprite.Shot;
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
    private List<Explosion> explosions;
    private List<Shot> shots;
    private List<Bullet> enemyBullets;
    private Player player;
    // private Shot shot;

    final int BLOCKHEIGHT = 50;
    final int BLOCKWIDTH = 50;

    // Weapon-tier bullet sprites, cut from the sheet (keyed transparent).
    private static final int[] BKEYS = {0x003663, 0x464646, 0x000000};
    private static final Image BULLET_PELLET = Images.tile(IMG_SPRITES, 246, 12, 12, 7, 2, BKEYS, 40);
    // Round, symmetric orb (no facing) so angled/rightward shots never look reversed.
    private static final Image BULLET_ORB = Images.tile(IMG_SPRITES, 288, 36, 10, 9, 3, BKEYS, 40);
    private static final Image BULLET_COMET = Images.tile(IMG_SPRITES, 380, 7, 29, 19, 2, BKEYS, 40);
    private static final double SHOT_SPEED = 12;
    private static final int FIRE_INTERVAL = 9; // frames between volleys while firing

    private boolean firing = false;
    private int fireTimer = 0;

    // Boss fight (Stage 7).
    private static final int BOSS_HP = 120;
    private Boss activeBoss;
    private int bossBannerTimer = 0;
    private int bossesBeaten = 0;

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
    private static final int POWERUP_DROP_PERCENT = 20;

    private Timer timer;
    private final Game game;

    // TODO load this map from a file
    private final int[][] MAP = {
        {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
        {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}
    };

    private SpawnSource spawnSource;
    private AudioPlayer audioPlayer;

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
        // shot = new Shot();
    }

    // Resets the whole run after a Game Over so the player can play again.
    private void restart() {
        frame = 0;
        deaths = 0;
        bossesBeaten = 0;
        activeBoss = null;
        bossBannerTimer = 0;
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

    private void drawMap(Graphics g) {
        // Draw scrolling starfield background

        // Calculate smooth scrolling offset (1 pixel per frame)
        int scrollOffset = (frame) % BLOCKWIDTH;

        // Calculate which columns to draw based on screen position
        int baseCol = (frame) / BLOCKWIDTH;
        int colsNeeded = (BOARD_WIDTH / BLOCKWIDTH) + 2; // +2 for smooth scrolling

        // Loop through columns that should be visible on screen
        for (int screenCol = 0; screenCol < colsNeeded; screenCol++) {
            // Calculate which MAP row to use (with wrapping) — MAP's outer
            // dimension is now the scrolling axis
            int mapRow = (baseCol + screenCol) % MAP.length;

            // Calculate X position for this column
            int x = BOARD_WIDTH - ( (screenCol * BLOCKWIDTH) - scrollOffset );

            // Skip if column is completely off-screen
            if (x > BOARD_WIDTH || x < -BLOCKWIDTH) {
                continue;
            }

            // Draw each row in this column
            for (int row = 0; row < MAP[mapRow].length; row++) {
                if (MAP[mapRow][row] == 1) {
                    // Calculate Y position
                    int y = row * BLOCKHEIGHT;

                    // Draw a cluster of stars
                    drawStarCluster(g, x, y, BLOCKWIDTH, BLOCKHEIGHT);
                }
            }
        }

    }

    private void drawStarCluster(Graphics g, int x, int y, int width, int height) {
        // Set star color to white
        g.setColor(Color.WHITE);

        // Draw multiple stars in a cluster pattern
        // Main star (larger)
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        g.fillOval(centerX - 2, centerY - 2, 4, 4);

        // Smaller surrounding stars
        g.fillOval(centerX - 15, centerY - 10, 2, 2);
        g.fillOval(centerX + 12, centerY - 8, 2, 2);
        g.fillOval(centerX - 8, centerY + 12, 2, 2);
        g.fillOval(centerX + 10, centerY + 15, 2, 2);

        // Tiny stars for more detail
        g.fillOval(centerX - 20, centerY + 5, 1, 1);
        g.fillOval(centerX + 18, centerY - 15, 1, 1);
        g.fillOval(centerX - 5, centerY - 18, 1, 1);
        g.fillOval(centerX + 8, centerY + 20, 1, 1);
    }

    private void drawAliens(Graphics g) {

        for (Enemy enemy : enemies) {

            if (enemy.isVisible()) {

                g.drawImage(enemy.getImage(), enemy.getX(), enemy.getY(), this);

                if (enemy.getHitFlash() > 0) {
                    // Brief white flash for hit feedback.
                    Graphics2D g2 = (Graphics2D) g;
                    Composite old = g2.getComposite();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                    g2.setColor(Color.WHITE);
                    g2.fillRect(enemy.getX(), enemy.getY(),
                            enemy.getImage().getWidth(null), enemy.getImage().getHeight(null));
                    g2.setComposite(old);
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

        List<Explosion> toRemove = new ArrayList<>();

        for (Explosion explosion : explosions) {

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

            drawMap(g);  // Draw background stars first
            drawExplosions(g);
            drawPowreUps(g);
            drawAliens(g);
            drawEnemyBullets(g);
            drawPlayer(g);
            drawShot(g);
            drawBoss(g);

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
            }

            int bx = bullet.getX();
            int by = bullet.getY();
            if (bx < -Bullet.SIZE || bx > BOARD_WIDTH || by < -Bullet.SIZE || by > BOARD_HEIGHT) {
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
                            int enemyX = enemy.getX();
                            int enemyY = enemy.getY();

                            enemy.setImage(Images.load(IMG_EXPLOSION));
                            enemy.setDying(true);
                            explosions.add(new Explosion(enemyX, enemyY));
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
            explosions.add(new Explosion(bx - 40, by - 30));
            explosions.add(new Explosion(bx + 20, by + 20));
            activeBoss = null;
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
        // Boss gate — spawn a real boss that holds back the next wave until killed.
        if (sd.type.startsWith("BOSS:")) {
            String name = sd.type.substring(5);
            Boss boss = new Boss(name, sd.x, sd.y, BOSS_HP);
            boss.setHomeX(BOARD_WIDTH - 200);
            enemies.add(boss);
            activeBoss = boss;
            bossBannerTimer = 150;
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
        }
    }

    // Fires the current weapon tier: sprite and shot pattern both scale up.
    private void fireWeapon() {
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
