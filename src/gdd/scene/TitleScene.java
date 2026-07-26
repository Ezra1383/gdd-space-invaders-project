package gdd.scene;

import gdd.AudioPlayer;
import gdd.Game;
import gdd.Images;
import static gdd.Global.*;
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
import javax.swing.JPanel;
import javax.swing.Timer;

public class TitleScene extends JPanel {

    private int frame = 0;
    private Image image;
    private AudioPlayer audioPlayer;
    private final Dimension d = new Dimension(BOARD_WIDTH, BOARD_HEIGHT);
    private Timer timer;
    private Game game;

    public TitleScene(Game game) {
        this.game = game;
        // initBoard();
        // initTitle();
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

        initTitle();
        initAudio();
    }

    public void stop() {
        try {
            if (timer != null) {
                timer.stop();
            }

            if (audioPlayer != null) {
                audioPlayer.stop();
            }
        } catch (Exception e) {
            System.err.println("Error closing audio player.");
        }
    }

    private void initTitle() {
        image = Images.load(IMG_TITLE);
    }

    private void initAudio() {
        try {
            String filePath = "src/audio/title.wav";
            audioPlayer = new AudioPlayer(filePath);

            audioPlayer.play();
        } catch (Exception e) {
            System.err.println("Error with playing sound.");
        }

    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        doDrawing(g);
    }

    private void doDrawing(Graphics g) {

        g.setColor(Color.black);
        g.fillRect(0, 0, d.width, d.height);

        g.drawImage(image, 0, -80, d.width, d.height, this);

        drawHowTo(g);

        if (frame % 60 < 30) {
            g.setColor(Color.red);
        } else {
            g.setColor(Color.white);
        }

        g.setFont(g.getFont().deriveFont(32f));
        String text = "Press SPACE to Start";
        int stringWidth = g.getFontMetrics().stringWidth(text);
        int x = (d.width - stringWidth) / 2;
        // int stringHeight = g.getFontMetrics().getAscent();
        // int y = (d.height + stringHeight) / 2;
        g.drawString(text, x, 600);

        g.setColor(Color.gray);
        g.setFont(g.getFont().deriveFont(10f));
        g.drawString("Game by Chayapol", 10, 650);

        Toolkit.getDefaultToolkit().sync();
    }

    /**
     * A compact controls + tips legend on a translucent panel, so a first-time
     * player knows the keys and the three things that make this game itself: the
     * tiny hitbox, grazing, and the clone/shield damage order.
     */
    private void drawHowTo(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        int px = 55;
        int pw = d.width - 110;
        int py = 355;
        int ph = 205;

        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f));
        g2.setColor(Color.black);
        g2.fillRoundRect(px, py, pw, ph, 18, 18);
        g2.setComposite(old);
        g2.setColor(new Color(90, 140, 200));
        g2.drawRoundRect(px, py, pw, ph, 18, 18);

        int x = px + 26;
        g.setColor(new Color(150, 200, 255));
        g.setFont(new Font("Helvetica", Font.BOLD, 22));
        g.drawString("HOW TO PLAY", x, py + 34);

        g.setColor(Color.white);
        g.setFont(new Font("Helvetica", Font.BOLD, 16));
        g.drawString("Arrows — Move          Space — Fire (hold)", x, py + 66);
        g.drawString("P — Pause               M — Mute", x, py + 90);

        g.setColor(new Color(255, 220, 120));
        g.setFont(new Font("Helvetica", Font.PLAIN, 15));
        g.drawString("• Your hitbox is a tiny dot — weave between the bullets", x, py + 124);
        g.drawString("• Graze bullets (near-misses) to rack up score", x, py + 148);
        g.drawString("• Clones & Shields soak hits — clones die first, and they shoot", x, py + 172);
    }

    private void update() {
        frame++;
    }

    private void doGameCycle() {
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

        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                game.loadScene2(); // start the game
            }
        }
    }
}
