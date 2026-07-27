package gdd.scene;

import gdd.Game;
import static gdd.Global.*;
import gdd.Images;
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

/**
 * The About screen: who made this game, for whom, and what it was built on.
 * Reached with A from the title screen, closed with ESC or A.
 *
 * <p>Everything shown here is a constant at the top of this class, so the
 * credits can be corrected without touching any drawing code.
 */
public class AboutScene extends JPanel {

    // --- the credits themselves ------------------------------------------
    public static final String UNIVERSITY = "Assumption University of Thailand";
    public static final String FACULTY =
            "Vincent Mary School of Engineering, Science and Technology";
    private static final String COURSE = "Game Design & Development";
    private static final String[][] TEAM = {
        {"Mohammad Rahemi", "6611695"},
        {"Mankirat Kaur", "6611585"},
    };
    // Not a TEAM row: those are name/student-ID pairs, and an advisor has no
    // ID — dropped in there his name renders in the dim ID column, reading as
    // a third student. He gets his own line under the list instead.
    private static final String ADVISOR = "Chayapol Moemeng";
    // The Drakengard 3 boss themes are the tracks shipped under the internal
    // names MUSIC_BOSS_B04B_B_SCD, MUSIC_BOSS_B01B_B_SCD and
    // MUSIC_BOSS_B05A_B_SCD — kept here rather than on screen, where the raw
    // asset IDs would mean nothing to a player.
    private static final String[] MUSIC = {
        "Final Descent — Aldous Ichnite",
        "Cutting Edge — Shane Ivers",
        "The Companionship of Isolation — Brylie Christopher",
        "Boss themes — Drakengard 3 (Square Enix)",
    };
    private static final String[] CREDITS = {
        "Ship, enemy & environment art — Foozle, \"Void\" pack",
        "Based on the Java Space Invaders project by Jan Bodnar",
        "Sound effects synthesized in code — no sample files",
    };

    private final Dimension d = new Dimension(BOARD_WIDTH, BOARD_HEIGHT);
    private final Game game;
    private int frame = 0;
    private Image backdrop;
    private Timer timer;
    private boolean wired = false; // key listener is added once, not per start()

    public AboutScene(Game game) {
        this.game = game;
    }

    public void start() {
        if (!wired) {
            addKeyListener(new TAdapter());
            wired = true;
        }
        setFocusable(true);
        requestFocusInWindow();
        setBackground(Color.black);

        if (backdrop == null) {
            backdrop = Images.load(IMG_TITLE);
        }
        if (timer == null) {
            timer = new Timer(1000 / 60, new GameCycle());
        }
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        doDrawing(g);
    }

    private void doDrawing(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        g.setColor(Color.black);
        g.fillRect(0, 0, d.width, d.height);

        // The title art, dimmed, so this reads as the same game.
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        g.drawImage(backdrop, 0, -80, d.width, d.height, this);
        g2.setComposite(old);

        // A translucent card over the art, matching the title screen's HOW TO
        // PLAY panel — without it the logo shows through the smaller text.
        int px = 40;
        int pw = d.width - 80;
        int py = 36;
        int ph = 602;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
        g2.setColor(Color.black);
        g2.fillRoundRect(px, py, pw, ph, 20, 20);
        g2.setComposite(old);
        g2.setColor(new Color(90, 140, 200));
        g2.drawRoundRect(px, py, pw, ph, 20, 20);

        int y = 88;

        g.setColor(new Color(150, 200, 255));
        centered(g, "ABOUT", new Font("Helvetica", Font.BOLD, 34), y);
        y += 22;
        g.setColor(new Color(90, 140, 200));
        g.drawLine(px + 100, y, px + pw - 100, y);

        // The institution — the reason this screen exists.
        y += 48;
        g.setColor(Color.white);
        centered(g, UNIVERSITY, new Font("Helvetica", Font.BOLD, 23), y);
        y += 27;
        g.setColor(new Color(210, 225, 245));
        centered(g, FACULTY, new Font("Helvetica", Font.PLAIN, 16), y);
        y += 25;
        g.setColor(new Color(255, 220, 120));
        centered(g, COURSE, new Font("Helvetica", Font.BOLD, 16), y);

        y += 46;
        section(g, "TEAM", y);
        y += 28;
        for (String[] member : TEAM) {
            var f = new Font("Helvetica", Font.PLAIN, 17);
            g.setFont(f);
            // Name right-aligned to the middle, ID left-aligned after it, so the
            // list lines up on the centre no matter how long the names are.
            int gap = 24;
            int nameW = getFontMetrics(f).stringWidth(member[0]);
            g.setColor(Color.white);
            g.drawString(member[0], d.width / 2 - gap - nameW, y);
            g.setColor(new Color(160, 170, 190));
            g.drawString(member[1], d.width / 2 + gap, y);
            y += 26;
        }

        y += 6;
        labelled(g, "Advised by  ", ADVISOR, y);

        y += 48;
        section(g,"MUSIC", y);
        y += 28;
        y = lines(g, MUSIC, y);

        y += 48;
        section(g,"ART & CODE", y);
        y += 28;
        y = lines(g, CREDITS, y);

        // Back hint, blinking like the title screen's start prompt.
        g.setColor(frame % 60 < 30 ? Color.yellow : new Color(150, 150, 150));
        centered(g, "Press ESC to go back", new Font("Helvetica", Font.BOLD, 18), d.height - 34);

        Toolkit.getDefaultToolkit().sync();
    }

    /** A section heading, in the same blue as the title screen's panel label. */
    private void section(Graphics g, String title, int y) {
        g.setColor(new Color(150, 200, 255));
        centered(g, title, new Font("Helvetica", Font.BOLD, 19), y);
    }

    /**
     * One centred line of a dim label followed by a bright value — used for
     * the advisor, who needs a role spelled out rather than a column position.
     */
    private void labelled(Graphics g, String label, String value, int y) {
        var lf = new Font("Helvetica", Font.PLAIN, 15);
        var vf = new Font("Helvetica", Font.BOLD, 17);
        int lw = getFontMetrics(lf).stringWidth(label);
        int vw = getFontMetrics(vf).stringWidth(value);
        int x = (d.width - lw - vw) / 2;
        g.setFont(lf);
        g.setColor(new Color(160, 170, 190));
        g.drawString(label, x, y);
        g.setFont(vf);
        g.setColor(Color.white);
        g.drawString(value, x + lw, y);
    }

    /**
     * A block of centred credit lines. Returns the baseline of the last line
     * drawn — not one step past it — so every section can be separated by the
     * same gap regardless of whether a block or a single line came before.
     */
    private int lines(Graphics g, String[] entries, int y) {
        g.setColor(new Color(180, 190, 205));
        var f = new Font("Helvetica", Font.PLAIN, 14);
        int last = y;
        for (String line : entries) {
            centered(g, line, f, y);
            last = y;
            y += 22;
        }
        return last;
    }

    private void centered(Graphics g, String text, Font font, int y) {
        g.setFont(font);
        int w = getFontMetrics(font).stringWidth(text);
        g.drawString(text, (d.width - w) / 2, y);
    }

    private class GameCycle implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            frame++;
            repaint();
        }
    }

    private class TAdapter extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent e) {
            int k = e.getKeyCode();
            if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_A || k == KeyEvent.VK_BACK_SPACE) {
                game.loadTitle();
            }
        }
    }
}
