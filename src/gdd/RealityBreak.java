package gdd;

import gdd.sprite.Boss;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JWindow;

/**
 * Nemesis's final phase: the fight outgrows the board and reality tears open at
 * the edges of the screen. Each tear is a separate borderless, non-focusable,
 * always-on-top window holding a red clone of the player's ship that charges and
 * fires in time with the boss.
 *
 * <p><b>Bonus layer, never load-bearing.</b> The attacks the tears show — the
 * vertical ray on the top/bottom axis, the sweeping rays on the left/right —
 * already exist and are fully telegraphed and dodgeable inside the main board.
 * The windows are spectacle on top: a tear is the <em>origin</em> of an attack,
 * not the only warning of it. So when a tear can't open (no desktop room on a
 * small screen, translucency unsupported, the effect switched off, or a headless
 * test) the fight plays identically, just without the flourish.
 *
 * <p>All Swing calls happen on the game's timer, which is the EDT, so this class
 * is not otherwise synchronized.
 */
public final class RealityBreak {

    /** Which side of the board a tear sits on; also which way its clone faces. */
    enum Edge { LEFT, RIGHT, TOP, BOTTOM }

    // A tear needs at least this much desktop beyond the board to fit.
    private static final int SIDE_MIN_GAP = 200; // left/right need width
    private static final int END_MIN_GAP = 120;  // top/bottom need height
    private static final int MARGIN = 12;         // clearance from board and screen edge

    private static final int SIDE_W = 210;
    private static final int SIDE_H = 320;
    private static final int END_W = 260;
    private static final int END_H = 140;

    /** Frames the snap-shut collapse takes when Nemesis dies. */
    private static final int COLLAPSE_FRAMES = 22;

    private final List<Satellite> satellites = new ArrayList<>();
    private Rectangle boardOnScreen;   // the play area in screen coordinates
    private Rectangle screenUsable;    // its monitor minus the taskbar
    private int collapseAge = -1;      // -1 until the tears start snapping shut

    /**
     * Tears the windows open around {@code board}. A no-op — leaving the fight
     * to play purely in-board — if the environment can't support them.
     */
    public void open(JComponent board) {
        if (!satellites.isEmpty() || GraphicsEnvironment.isHeadless() || !board.isShowing()) {
            return;
        }
        Point origin;
        try {
            origin = board.getLocationOnScreen();
        } catch (IllegalComponentStateException e) {
            return; // not on screen after all
        }
        boardOnScreen = new Rectangle(origin.x, origin.y, board.getWidth(), board.getHeight());

        GraphicsConfiguration gc = board.getGraphicsConfiguration();
        GraphicsDevice device = gc.getDevice();
        Rectangle screen = gc.getBounds();
        Insets ins = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        screenUsable = new Rectangle(screen.x + ins.left, screen.y + ins.top,
                screen.width - ins.left - ins.right, screen.height - ins.top - ins.bottom);

        boolean translucent = device.isWindowTranslucencySupported(
                GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);

        BufferedImage jet = Boss.redJet();
        Map<Edge, Rectangle> plan = layout(boardOnScreen, screenUsable);
        for (Map.Entry<Edge, Rectangle> e : plan.entrySet()) {
            satellites.add(new Satellite(e.getKey(), e.getValue(), jet, translucent));
        }
    }

    /** True once at least one tear is open. */
    public boolean isOpen() {
        return !satellites.isEmpty();
    }

    /**
     * Syncs every tear to the boss and repaints them. Top/bottom follow the
     * vertical ray's column and mirror its charge/fire; left/right follow the
     * sweeping rays. Each tear therefore lights up exactly when its axis's
     * in-board hazard does.
     */
    public void update(Boss n) {
        if (satellites.isEmpty()) {
            return;
        }
        boolean vCharge = n.isVerticalCharging();
        boolean vFire = n.isVerticalFiring();
        boolean hCharge = n.isBeamCharging();
        boolean hFire = n.isBeamFiring();
        int rayX = n.getVerticalRay();

        for (Satellite s : satellites) {
            boolean vertical = s.edge == Edge.TOP || s.edge == Edge.BOTTOM;
            s.setState(vertical ? vCharge : hCharge, vertical ? vFire : hFire);
            if (vertical) {
                // Sit above/below the ray's column while it's active, else drift
                // back to centre — the tear lurches into place as the ray forms.
                int targetCentre = rayX == Integer.MIN_VALUE
                        ? boardOnScreen.x + boardOnScreen.width / 2
                        : boardOnScreen.x + rayX;
                s.followX(targetCentre, screenUsable);
            }
            s.tick();
        }
    }

    /**
     * Starts the tears snapping shut — reality reknitting as Nemesis dies.
     * Drive it with {@link #tickCollapse()}; it finishes by disposing the
     * windows. A no-op if nothing is open or a collapse is already running.
     */
    public void collapse() {
        if (satellites.isEmpty() || collapseAge >= 0) {
            return;
        }
        collapseAge = 0;
        for (Satellite s : satellites) {
            s.beginCollapse();
        }
    }

    /** Advances a running collapse; disposes the tears when it completes. */
    public void tickCollapse() {
        if (collapseAge < 0 || satellites.isEmpty()) {
            return;
        }
        collapseAge++;
        float t = Math.min(1f, collapseAge / (float) COLLAPSE_FRAMES);
        for (Satellite s : satellites) {
            s.collapseTo(t);
        }
        if (collapseAge >= COLLAPSE_FRAMES) {
            close();
        }
    }

    /** True once every tear is gone (also true before any ever opened). */
    public boolean isClosed() {
        return satellites.isEmpty();
    }

    /** Closes every tear. Safe to call repeatedly. */
    public void close() {
        for (Satellite s : satellites) {
            s.dispose();
        }
        satellites.clear();
        collapseAge = -1;
    }

    /**
     * Which edges have room, and the window rectangle for each — pure geometry,
     * so it can be checked without a display.
     */
    static Map<Edge, Rectangle> layout(Rectangle board, Rectangle screen) {
        Map<Edge, Rectangle> out = new EnumMap<>(Edge.class);

        int leftGap = board.x - screen.x;
        if (leftGap >= SIDE_MIN_GAP) {
            int w = Math.min(SIDE_W, leftGap - 2 * MARGIN);
            int h = Math.min(SIDE_H, board.height);
            out.put(Edge.LEFT, new Rectangle(board.x - MARGIN - w,
                    board.y + (board.height - h) / 2, w, h));
        }
        int rightGap = (screen.x + screen.width) - (board.x + board.width);
        if (rightGap >= SIDE_MIN_GAP) {
            int w = Math.min(SIDE_W, rightGap - 2 * MARGIN);
            int h = Math.min(SIDE_H, board.height);
            out.put(Edge.RIGHT, new Rectangle(board.x + board.width + MARGIN,
                    board.y + (board.height - h) / 2, w, h));
        }
        int topGap = board.y - screen.y;
        if (topGap >= END_MIN_GAP) {
            int h = Math.min(END_H, topGap - 2 * MARGIN);
            int w = Math.min(END_W, board.width);
            out.put(Edge.TOP, new Rectangle(board.x + (board.width - w) / 2,
                    board.y - MARGIN - h, w, h));
        }
        int bottomGap = (screen.y + screen.height) - (board.y + board.height);
        if (bottomGap >= END_MIN_GAP) {
            int h = Math.min(END_H, bottomGap - 2 * MARGIN);
            int w = Math.min(END_W, board.width);
            out.put(Edge.BOTTOM, new Rectangle(board.x + (board.width - w) / 2,
                    board.y + board.height + MARGIN, w, h));
        }
        return out;
    }

    // --- one tear ---------------------------------------------------------

    private static final class Satellite {
        private final Edge edge;
        private final JWindow window;
        private final BufferedImage clone; // oriented to face the board
        private final int[] dir;           // unit vector toward the board
        private final Random cracks = new Random();

        private int age;
        private float intensity; // 0 idle .. 1 firing, eased for a smooth glow
        private boolean charging;
        private boolean firing;
        private Rectangle collapseFrom; // bounds when the snap-shut began
        private float collapse = -1f;   // -1 not collapsing, else 0..1 progress

        Satellite(Edge edge, Rectangle bounds, BufferedImage jet, boolean translucent) {
            this.edge = edge;
            this.clone = orient(jet, edge);
            this.dir = switch (edge) {
                case LEFT -> new int[]{1, 0};
                case RIGHT -> new int[]{-1, 0};
                case TOP -> new int[]{0, 1};
                case BOTTOM -> new int[]{0, -1};
            };
            window = new JWindow();
            window.setFocusableWindowState(false);
            window.setAutoRequestFocus(false);
            window.setAlwaysOnTop(true);
            if (translucent) {
                try {
                    window.setBackground(new Color(0, 0, 0, 0));
                } catch (Exception ignored) {
                    // Some platforms report support then refuse the colour.
                }
            }
            window.setContentPane(new Panel(this));
            window.setBounds(bounds);
            window.setVisible(true);
        }

        void setState(boolean charging, boolean firing) {
            this.charging = charging;
            this.firing = firing;
        }

        /** Recentres a top/bottom tear on a screen-x, clamped to its monitor. */
        void followX(int centreX, Rectangle screen) {
            int w = window.getWidth();
            int x = centreX - w / 2;
            x = Math.max(screen.x + MARGIN, Math.min(x, screen.x + screen.width - MARGIN - w));
            window.setLocation(x, window.getY());
        }

        void tick() {
            age++;
            float target = firing ? 1f : charging ? 0.65f : 0.28f;
            intensity += (target - intensity) * 0.25f; // ease toward the target
            window.getContentPane().repaint();
        }

        void beginCollapse() {
            collapseFrom = window.getBounds();
            collapse = 0f;
        }

        /** Retracts the tear toward the board edge it faces, searing shut. */
        void collapseTo(float t) {
            collapse = t;
            int ow = collapseFrom.width;
            int oh = collapseFrom.height;
            int ox = collapseFrom.x;
            int oy = collapseFrom.y;
            int nx = ox;
            int ny = oy;
            int nw = ow;
            int nh = oh;
            if (dir[0] > 0) {          // faces right → retract to its right edge
                nw = Math.max(2, (int) (ow * (1 - t)));
                nx = ox + ow - nw;
            } else if (dir[0] < 0) {   // faces left → retract to its left edge
                nw = Math.max(2, (int) (ow * (1 - t)));
            } else if (dir[1] > 0) {   // faces down → retract to its bottom
                nh = Math.max(2, (int) (oh * (1 - t)));
                ny = oy + oh - nh;
            } else {                   // faces up → retract to its top
                nh = Math.max(2, (int) (oh * (1 - t)));
            }
            window.setBounds(nx, ny, nw, nh);
            window.getContentPane().repaint();
        }

        void dispose() {
            window.dispose();
        }

        private void paint(Graphics2D g, int w, int h) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Collapsing: a searing white sliver that fades as it retracts.
            if (collapse >= 0f) {
                int a = (int) (235 * Math.max(0f, 1f - collapse));
                g.setColor(new Color(255, 235, 245, a));
                g.fillRect(0, 0, w, h);
                return;
            }

            // A quick birth flare so the tear reads as ripping open.
            float birth = Math.min(1f, age / 12f);

            // Board-facing edge, where the glow and cracks concentrate.
            int mx = w / 2 + dir[0] * (int) (Math.min(w, h) * 0.32);
            int my = h / 2 + dir[1] * (int) (Math.min(w, h) * 0.32);

            // Dark backing (matters when translucency is unavailable).
            g.setColor(new Color(12, 2, 8, (int) (140 * birth)));
            g.fillRect(0, 0, w, h);

            // Red glow pooling at the board-facing edge.
            int glow = (int) (Math.max(w, h) * (0.5 + 0.4 * intensity));
            Color hot = new Color(255, 60, 80, (int) (170 * intensity * birth));
            g.setPaint(new GradientPaint(mx, my, hot, mx - dir[0] * glow, my - dir[1] * glow,
                    new Color(255, 60, 80, 0)));
            g.fillRect(0, 0, w, h);

            // The clone, translucent, breathing slightly.
            float ca = 0.55f + 0.35f * intensity;
            java.awt.Composite old = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, Math.min(1f, ca * birth)));
            int cw = clone.getWidth();
            int ch = clone.getHeight();
            g.drawImage(clone, w / 2 - cw / 2 - dir[0] * 6, h / 2 - ch / 2 - dir[1] * 6, null);
            g.setComposite(old);

            // Charging orb / firing beam at the muzzle.
            if (charging) {
                int r = (int) (6 + 14 * intensity);
                if ((age / 4) % 2 == 0) {
                    g.setColor(new Color(255, 200, 220));
                    g.fillOval(mx - r / 2, my - r / 2, r, r);
                }
            } else if (firing) {
                g.setColor(new Color(255, 235, 250, 230));
                int t = 10;
                if (dir[0] != 0) {
                    int x0 = dir[0] > 0 ? mx : 0;
                    int x1 = dir[0] > 0 ? w : mx;
                    g.fillRect(Math.min(x0, x1), my - t / 2, Math.abs(x1 - x0), t);
                } else {
                    int y0 = dir[1] > 0 ? my : 0;
                    int y1 = dir[1] > 0 ? h : my;
                    g.fillRect(mx - t / 2, Math.min(y0, y1), t, Math.abs(y1 - y0));
                }
            }

            // Jagged fracture lines along the board-facing edge.
            cracks.setSeed(edge.ordinal() * 97L);
            g.setColor(new Color(255, 120, 140, (int) (150 * birth)));
            for (int i = 0; i < 5; i++) {
                int sx = mx, sy = my;
                for (int seg = 0; seg < 4; seg++) {
                    int ex = sx - dir[0] * (10 + cracks.nextInt(24)) + cracks.nextInt(21) - 10;
                    int ey = sy - dir[1] * (10 + cracks.nextInt(24)) + cracks.nextInt(21) - 10;
                    g.drawLine(sx, sy, ex, ey);
                    sx = ex;
                    sy = ey;
                }
            }
        }

        /** Rotates/flips the right-facing jet so it faces the board from `edge`. */
        private static BufferedImage orient(BufferedImage jet, Edge edge) {
            return switch (edge) {
                case LEFT -> jet;                       // already faces right
                case RIGHT -> flip(jet);                // face left
                case TOP -> rotate(jet, Math.PI / 2);   // face down
                case BOTTOM -> rotate(jet, -Math.PI / 2); // face up
            };
        }

        private static BufferedImage flip(BufferedImage src) {
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(src, src.getWidth(), 0, 0, src.getHeight(),
                    0, 0, src.getWidth(), src.getHeight(), null);
            g.dispose();
            return out;
        }

        private static BufferedImage rotate(BufferedImage src, double radians) {
            int w = src.getWidth();
            int h = src.getHeight();
            BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            AffineTransform at = new AffineTransform();
            at.translate(out.getWidth() / 2.0, out.getHeight() / 2.0);
            at.rotate(radians);
            at.translate(-w / 2.0, -h / 2.0);
            g.drawImage(src, at, null);
            g.dispose();
            return out;
        }
    }

    private static final class Panel extends JComponent {
        private final Satellite owner;

        Panel(Satellite owner) {
            this.owner = owner;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            owner.paint((Graphics2D) g.create(), getWidth(), getHeight());
        }
    }
}
