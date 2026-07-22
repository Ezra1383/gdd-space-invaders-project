package gdd;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Loads the Nairan ship GIFs into game-ready animation frames.
 *
 * The pack ships *preview* GIFs, so each needs cleaning before use:
 * <ul>
 *   <li>the ship's name is baked into the top of the canvas — cropped off;</li>
 *   <li>GIF has no alpha, so the flat {@code #2E222F} background is colour-keyed
 *       to transparent (without this, stacking layers just hides the ship);</li>
 *   <li>the ships face up, but our enemies fly left — every frame is rotated
 *       90° counter-clockwise so exhaust trails behind them.</li>
 * </ul>
 *
 * Base and Engine are composited per-frame into the ship loop. Weapons are
 * cropped with the <em>same</em> box as the ship so the muzzle flash lines up
 * when drawn as an overlay. Shields are cropped to their own bounds and drawn
 * centred, since the shield bubble extends past the hull.
 */
public final class GifSprites {

    private static final String ROOT = "src/images/Nairan/";
    private static final int BG = 0x2E222F; // flat preview background
    private static final int TOL2 = 900;    // squared colour distance tolerance
    /**
     * Rows reserved for the baked-in ship name. Measured across the whole pack
     * the label always ends by row 44 (on both the 320 and 640 canvases) while
     * the earliest ship pixel is row 65, so discarding the top 50 rows is safe.
     * Needed because on some ships (e.g. Frigate) the hull touches the label,
     * merging them into one band and defeating gap detection.
     */
    private static final int LABEL_ROWS = 50;

    // Concurrent: preloading runs on a background thread while the title shows.
    private static final Map<String, BufferedImage[]> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Rectangle> BOXES = new ConcurrentHashMap<>();

    private GifSprites() {
    }

    /**
     * Decodes every sprite the game uses so nothing hitches mid-run.
     * Safe to call off the EDT; the caches are concurrent.
     */
    public static void preload() {
        for (gdd.sprite.EnemyType t : gdd.sprite.EnemyType.values()) {
            ship(t.ship, t.spriteSize);
            weapons(t.ship, t.spriteSize);
            shields(t.ship, t.spriteSize);
            destruction(t.ship, t.spriteSize + 24);
        }
        String boss = gdd.sprite.Boss.SHIP;
        ship(boss, 130);
        weapons(boss, 130);
        shields(boss, 130);
        destruction(boss, 190);
        destruction("Fighter", 70); // boss secondary blasts
        for (gdd.sprite.ProjectileType p : gdd.sprite.ProjectileType.values()) {
            projectileRotations(p.gif, p.size, gdd.sprite.ProjectileType.STEPS);
        }
        beam(130);
    }

    /** Animated ship (Base + Engine composited), rotated to face left. */
    public static BufferedImage[] ship(String name, int targetMax) {
        return CACHE.computeIfAbsent("ship:" + name + ":" + targetMax, k -> {
            BufferedImage[] comp = shipComposite(name);
            if (comp.length == 0) {
                return new BufferedImage[0];
            }
            return finish(comp, shipBox(name), targetMax);
        });
    }

    /**
     * One-shot weapon-fire overlay, cropped with the same box as {@link #ship}
     * so it can be drawn straight on top at the same position.
     */
    public static BufferedImage[] weapons(String name, int targetMax) {
        return CACHE.computeIfAbsent("wpn:" + name + ":" + targetMax, k -> {
            BufferedImage[] raw = readGif(ROOT + "Weapons/Nairan - " + name + " - Weapons.gif");
            if (raw.length == 0) {
                return new BufferedImage[0]; // e.g. Bomber has no weapons layer
            }
            BufferedImage[] keyed = keyAll(raw);
            return finish(keyed, shipBox(name), targetMax);
        });
    }

    /**
     * Shield-hit flare. This layer is the whole ship silhouette outlined in red
     * (not a separate bubble), so it must render at exactly the ship's scale or
     * the enemy appears to grow and shrink when hit.
     *
     * @param shipTargetMax the ship's own target size — NOT an inflated one.
     *                      The crop is a box centred on the ship's centre that
     *                      still contains the outline's slight overhang, scaled
     *                      by the same factor as the ship, so drawing it centred
     *                      on the ship lines up exactly.
     */
    public static BufferedImage[] shields(String name, int shipTargetMax) {
        return CACHE.computeIfAbsent("shd:" + name + ":" + shipTargetMax, k -> {
            BufferedImage[] raw = readGif(ROOT + "Shields/Nairan - " + name + " - Shield.gif");
            if (raw.length == 0) {
                return new BufferedImage[0];
            }
            BufferedImage[] keyed = keyAll(raw);
            Rectangle sb = shipBox(name);
            Rectangle own = ownBox(name, keyed);
            // Smallest box centred on the ship centre that contains the shield.
            double cx = sb.getCenterX();
            double cy = sb.getCenterY();
            double halfW = Math.max(cx - own.getMinX(), own.getMaxX() - cx);
            double halfH = Math.max(cy - own.getMinY(), own.getMaxY() - cy);
            Rectangle box = new Rectangle(
                    (int) Math.floor(cx - halfW), (int) Math.floor(cy - halfH),
                    (int) Math.ceil(halfW * 2), (int) Math.ceil(halfH * 2));
            // Same pixels-per-source-pixel as the ship.
            double scale = shipTargetMax / (double) Math.max(sb.width, sb.height);
            int target = Math.max(1, (int) Math.round(Math.max(box.width, box.height) * scale));
            return finish(keyed, box, target);
        });
    }

    /** One-shot destruction animation, rotated to match the ships. */
    public static BufferedImage[] destruction(String name, int targetMax) {
        return CACHE.computeIfAbsent("boom:" + name + ":" + targetMax, k -> {
            // Filenames in this folder have inconsistent spacing around the dash.
            BufferedImage[] raw = readGif(ROOT + "Destruction/Nairan - " + name + " -  Destruction.gif");
            if (raw.length == 0) {
                raw = readGif(ROOT + "Destruction/Nairan - " + name + "  -  Destruction.gif");
            }
            if (raw.length == 0) {
                return new BufferedImage[0];
            }
            BufferedImage[] keyed = keyAll(raw);
            return finish(keyed, ownBox(name, keyed), targetMax);
        });
    }

    /**
     * A projectile pre-rendered at `steps` rotations around the circle, so a
     * bullet can pick the sprite matching its travel angle. Index 0 points
     * right (angle 0); index i covers angle i*2PI/steps.
     */
    public static BufferedImage[] projectileRotations(String gifName, int targetMax, int steps) {
        return CACHE.computeIfAbsent("proj:" + gifName + ":" + targetMax + ":" + steps, k -> {
            BufferedImage[] raw = readGif(ROOT + "Projectiles/Nairan - " + gifName + ".gif");
            if (raw.length == 0) {
                return new BufferedImage[0];
            }
            // Projectiles have no name label; use the mid frame as representative.
            BufferedImage src = key(raw[raw.length / 2]);
            Rectangle box = contentBox(new BufferedImage[]{src}, -1);
            src = scaleTo(src.getSubimage(box.x, box.y, box.width, box.height), targetMax);
            BufferedImage[] out = new BufferedImage[steps];
            for (int i = 0; i < steps; i++) {
                // Source art points UP; +90° makes angle 0 point right.
                out[i] = rotate(src, (i * 2 * Math.PI / steps) + Math.PI / 2);
            }
            return out;
        });
    }

    /** The Ray beam segment, rotated to fire leftwards (boss signature move). */
    public static BufferedImage[] beam(int targetMax) {
        return CACHE.computeIfAbsent("beam:" + targetMax, k -> {
            BufferedImage[] raw = readGif(ROOT + "Projectiles/Nairan - Ray.gif");
            if (raw.length == 0) {
                return new BufferedImage[0];
            }
            BufferedImage[] keyed = keyAll(raw);
            Rectangle box = contentBox(keyed, -1);
            BufferedImage[] out = new BufferedImage[keyed.length];
            for (int i = 0; i < keyed.length; i++) {
                out[i] = scaleTo(rotateCCW(keyed[i].getSubimage(box.x, box.y, box.width, box.height)),
                        targetMax);
            }
            return out;
        });
    }

    // --- boxes ------------------------------------------------------------

    /** Ship bounds (Base+Engine union), shared by the weapons overlay. */
    private static Rectangle shipBox(String name) {
        return BOXES.computeIfAbsent("ship:" + name, k -> {
            BufferedImage[] comp = shipComposite(name);
            if (comp.length == 0) {
                return new Rectangle(0, 0, 1, 1);
            }
            BufferedImage[] base = readGif(ROOT + "Bases/Nairan - " + name + " - Base.gif");
            return contentBox(comp, labelSkip(base.length > 0 ? base[0] : comp[0]));
        });
    }

    /** Bounds of a layer in its own right, with the ship's label band skipped. */
    private static Rectangle ownBox(String name, BufferedImage[] keyed) {
        BufferedImage[] base = readGif(ROOT + "Bases/Nairan - " + name + " - Base.gif");
        // Take the label band from the Base gif: on a destruction/shield frame
        // the effect can touch the text, merging bands and defeating detection.
        int skip = labelSkip(base.length > 0 ? base[0] : keyed[0]);
        return contentBox(keyed, skip);
    }

    private static BufferedImage[] shipComposite(String name) {
        BufferedImage[] base = readGif(ROOT + "Bases/Nairan - " + name + " - Base.gif");
        if (base.length == 0) {
            return new BufferedImage[0];
        }
        BufferedImage[] engine = readGif(ROOT + "Engines/Nairan - " + name + " - Engine.gif");
        BufferedImage[] comp = new BufferedImage[base.length];
        for (int i = 0; i < base.length; i++) {
            BufferedImage b = key(base[i]);
            BufferedImage c = new BufferedImage(b.getWidth(), b.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = c.createGraphics();
            g.drawImage(b, 0, 0, null);
            if (engine.length > 0) {
                g.drawImage(key(engine[i % engine.length]), 0, 0, null);
            }
            g.dispose();
            comp[i] = c;
        }
        return comp;
    }

    // --- pipeline ---------------------------------------------------------

    /** Crop to `box`, rotate 90° CCW (face left), scale so the longest side == targetMax. */
    private static BufferedImage[] finish(BufferedImage[] frames, Rectangle box, int targetMax) {
        Rectangle safe = clamp(box, frames[0].getWidth(), frames[0].getHeight());
        BufferedImage[] out = new BufferedImage[frames.length];
        for (int i = 0; i < frames.length; i++) {
            BufferedImage cropped = frames[i].getSubimage(safe.x, safe.y, safe.width, safe.height);
            out[i] = scaleTo(rotateCCW(cropped), targetMax);
        }
        return out;
    }

    private static Rectangle clamp(Rectangle r, int w, int h) {
        int x = Math.max(0, Math.min(r.x, w - 1));
        int y = Math.max(0, Math.min(r.y, h - 1));
        return new Rectangle(x, y, Math.max(1, Math.min(r.width, w - x)),
                Math.max(1, Math.min(r.height, h - y)));
    }

    private static BufferedImage[] keyAll(BufferedImage[] src) {
        BufferedImage[] out = new BufferedImage[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = key(src[i]);
        }
        return out;
    }

    /** Colour-keys the flat preview background to transparent (bulk pixel op). */
    private static BufferedImage key(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < px.length; i++) {
            int argb = px[i];
            if ((argb >>> 24) < 16) {
                px[i] = 0;
                continue;
            }
            int rgb = argb & 0xFFFFFF;
            px[i] = isBg(rgb) ? 0 : (0xFF000000 | rgb);
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private static boolean isBg(int rgb) {
        int dr = ((rgb >> 16) & 255) - ((BG >> 16) & 255);
        int dg = ((rgb >> 8) & 255) - ((BG >> 8) & 255);
        int db = (rgb & 255) - (BG & 255);
        return dr * dr + dg * dg + db * db < TOL2;
    }

    /** Rows to discard from the top so the baked-in ship name never shows. */
    private static int labelSkip(BufferedImage frame) {
        return Math.max(textBandEnd(frame), LABEL_ROWS - 1);
    }

    /**
     * Bottom row of the first content band — the baked-in ship name. Returns -1
     * if there's only one band (nothing to skip).
     */
    private static int textBandEnd(BufferedImage frame) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        int[] px = frame.getRGB(0, 0, w, h, null, 0, w);
        int firstEnd = -1;
        int bands = 0;
        int start = -1;
        for (int y = 0; y < h; y++) {
            boolean any = false;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if (!isBg(px[row + x] & 0xFFFFFF)) {
                    any = true;
                    break;
                }
            }
            if (any && start < 0) {
                start = y;
            }
            if (!any && start >= 0) {
                bands++;
                if (bands == 1) {
                    firstEnd = y - 1;
                }
                start = -1;
            }
        }
        if (start >= 0) {
            bands++;
        }
        return bands >= 2 ? firstEnd : -1;
    }

    /** Union bounding box of visible pixels across all frames, below `skipRows`. */
    private static Rectangle contentBox(BufferedImage[] frames, int skipRows) {
        int minx = Integer.MAX_VALUE;
        int miny = Integer.MAX_VALUE;
        int maxx = -1;
        int maxy = -1;
        for (BufferedImage f : frames) {
            int w = f.getWidth();
            int h = f.getHeight();
            int[] px = f.getRGB(0, 0, w, h, null, 0, w);
            for (int y = Math.max(0, skipRows + 1); y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    if (((px[row + x] >>> 24) & 255) > 16) {
                        if (x < minx) {
                            minx = x;
                        }
                        if (x > maxx) {
                            maxx = x;
                        }
                        if (y < miny) {
                            miny = y;
                        }
                        if (y > maxy) {
                            maxy = y;
                        }
                    }
                }
            }
        }
        if (maxx < 0) {
            return new Rectangle(0, 0, frames[0].getWidth(), frames[0].getHeight());
        }
        return new Rectangle(minx, miny, maxx - minx + 1, maxy - miny + 1);
    }

    private static BufferedImage rotateCCW(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getHeight(), src.getWidth(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.rotate(Math.toRadians(-90));
        g.drawImage(src, -src.getWidth(), 0, null);
        g.dispose();
        return out;
    }

    /** Free rotation about the centre, on a canvas big enough to avoid clipping. */
    private static BufferedImage rotate(BufferedImage src, double radians) {
        int w = src.getWidth();
        int h = src.getHeight();
        int d = (int) Math.ceil(Math.sqrt(w * w + h * h));
        BufferedImage out = new BufferedImage(d, d, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        AffineTransform at = AffineTransform.getRotateInstance(radians, d / 2.0, d / 2.0);
        at.translate((d - w) / 2.0, (d - h) / 2.0);
        g.drawImage(src, at, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scaleTo(BufferedImage src, int targetMax) {
        int longest = Math.max(src.getWidth(), src.getHeight());
        if (longest <= 0) {
            return src;
        }
        double s = targetMax / (double) longest;
        int w = Math.max(1, (int) Math.round(src.getWidth() * s));
        int h = Math.max(1, (int) Math.round(src.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage[] readGif(String path) {
        File f = new File(path);
        if (!f.exists()) {
            return new BufferedImage[0];
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(f)) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) {
                return new BufferedImage[0];
            }
            ImageReader r = it.next();
            r.setInput(in);
            int n = r.getNumImages(true);
            BufferedImage[] out = new BufferedImage[n];
            for (int i = 0; i < n; i++) {
                out[i] = r.read(i);
            }
            r.dispose();
            return out;
        } catch (Exception e) {
            System.err.println("Failed to read GIF: " + path + " (" + e.getMessage() + ")");
            return new BufferedImage[0];
        }
    }
}
