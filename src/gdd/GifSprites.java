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
 * Loads a {@link Faction}'s ship pack into game-ready animation frames.
 *
 * The packs ship *preview* art, so each layer needs cleaning before use:
 * <ul>
 *   <li>the ship's name is baked into the top of the canvas — cropped off;</li>
 *   <li>the background is a flat opaque {@code #2E222F}, colour-keyed to
 *       transparent (without this, stacking layers just hides the ship);</li>
 *   <li>the ships face up, but our enemies fly left — every frame is rotated
 *       90° counter-clockwise so exhaust trails behind them.</li>
 * </ul>
 *
 * Base and Engine are composited per-frame into the ship loop. Weapons are
 * cropped with the <em>same</em> box as the ship so the muzzle flash lines up
 * when drawn as an overlay. Shields are the ship silhouette outlined in red, so
 * they render at exactly the ship's scale (see {@link #shields}).
 *
 * Files are located by matching normalised name segments rather than exact
 * paths, because the packs are inconsistent about folder names, casing and
 * spacing (see {@link Faction}).
 */
public final class GifSprites {

    private static final int BG = 0x2E222F; // flat preview background
    private static final int TOL2 = 900;    // squared colour distance tolerance
    /**
     * Fallback rows to discard for the baked-in ship name, used only when band
     * detection can't separate the label from the hull (Nairan's Frigate hull
     * touches its label, merging them into one band). Measured across both
     * packs the label always ends by row 54 and the earliest ship pixel is row
     * 55, so detection normally wins; this is the floor when it doesn't.
     */
    private static final int LABEL_ROWS = 50;

    // Concurrent: preloading runs on a background thread while the title shows.
    private static final Map<String, BufferedImage[]> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Rectangle> BOXES = new ConcurrentHashMap<>();
    private static final Map<String, File[]> DIRS = new ConcurrentHashMap<>();

    private GifSprites() {
    }

    /**
     * Decodes every sprite the game uses so nothing hitches mid-run.
     * Safe to call off the EDT; the caches are concurrent.
     *
     * Factions are done in declaration order so the opening biome's art is
     * ready first — later biomes are minutes away and finish long before then.
     */
    public static void preload() {
        for (Faction f : Faction.values()) {
            if (layerDir(f, "base").length == 0) {
                continue; // faction with no gif-pack layout (e.g. the Void ships)
            }
            for (gdd.sprite.EnemyType t : gdd.sprite.EnemyType.values()) {
                if (t.faction != f) {
                    continue;
                }
                ship(f, t.ship, t.spriteSize);
                weapons(f, t.ship, t.spriteSize);
                shields(f, t.ship, t.spriteSize);
                destruction(f, t.ship, t.spriteSize + 24);
            }
            String boss = gdd.sprite.Boss.SHIP;
            ship(f, boss, gdd.sprite.Boss.SPRITE_SIZE);
            weapons(f, boss, gdd.sprite.Boss.SPRITE_SIZE);
            shields(f, boss, gdd.sprite.Boss.SPRITE_SIZE);
            destruction(f, boss, 190);
            destruction(f, "Fighter", 70); // boss secondary blasts
            for (gdd.sprite.ProjectileType p : gdd.sprite.ProjectileType.values()) {
                if (p.faction == f) {
                    projectileRotations(f, p.gif, p.size, gdd.sprite.ProjectileType.STEPS);
                }
            }
            beam(f, gdd.sprite.Boss.SPRITE_SIZE);
            // Wave walls, at every height the boss can size one to.
            for (int size : gdd.sprite.Boss.waveSlotSizes()) {
                wave(f, size);
            }
        }
    }

    /** Animated ship (Base + Engine composited), rotated to face left. */
    public static BufferedImage[] ship(Faction f, String name, int targetMax) {
        return CACHE.computeIfAbsent(key(f, "ship", name, targetMax), k -> {
            BufferedImage[] comp = shipComposite(f, name);
            if (comp.length == 0) {
                return new BufferedImage[0];
            }
            return finish(comp, shipBox(f, name), targetMax);
        });
    }

    /**
     * One-shot weapon-fire overlay, cropped with the same box as {@link #ship}
     * so it can be drawn straight on top at the same position.
     */
    public static BufferedImage[] weapons(Faction f, String name, int targetMax) {
        return CACHE.computeIfAbsent(key(f, "wpn", name, targetMax), k -> {
            BufferedImage[] raw = readFrames(file(f, "weapon", name));
            if (raw.length == 0) {
                return new BufferedImage[0]; // e.g. Bomber has no weapons layer
            }
            return finish(keyAll(raw), shipBox(f, name), targetMax);
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
    public static BufferedImage[] shields(Faction f, String name, int shipTargetMax) {
        return CACHE.computeIfAbsent(key(f, "shd", name, shipTargetMax), k -> {
            BufferedImage[] raw = readFrames(file(f, "shield", name));
            if (raw.length == 0) {
                return new BufferedImage[0]; // e.g. Kla'ed Support ship has none
            }
            BufferedImage[] keyed = keyAll(raw);
            Rectangle sb = shipBox(f, name);
            Rectangle own = ownBox(f, name, keyed);
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
    public static BufferedImage[] destruction(Faction f, String name, int targetMax) {
        return CACHE.computeIfAbsent(key(f, "boom", name, targetMax), k -> {
            BufferedImage[] raw = readFrames(file(f, "destruction", name));
            if (raw.length == 0) {
                return new BufferedImage[0];
            }
            BufferedImage[] keyed = keyAll(raw);
            return finish(keyed, ownBox(f, name, keyed), targetMax);
        });
    }

    /**
     * A projectile pre-rendered at `steps` rotations around the circle, so a
     * bullet can pick the sprite matching its travel angle. Index 0 points
     * right (angle 0); index i covers angle i*2PI/steps.
     */
    public static BufferedImage[] projectileRotations(Faction f, String gifName,
                                                      int targetMax, int steps) {
        return CACHE.computeIfAbsent(key(f, "proj", gifName, targetMax) + ":" + steps, k -> {
            BufferedImage[] raw = readFrames(projectile(f, gifName));
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
    public static BufferedImage[] beam(Faction f, int targetMax) {
        return CACHE.computeIfAbsent(key(f, "beam", "Ray", targetMax), k -> {
            BufferedImage[] raw = readFrames(projectile(f, "Ray"));
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

    /**
     * The Kla'ed Wave, sized so its long axis is {@code targetMax}.
     *
     * In the source it's a broad arc fired forward by an upward-facing ship;
     * the shared rotate-to-face-left step turns it into a vertical wall, which
     * is how the boss uses it. Frames are cropped with one union box so the
     * wall neither jumps nor resizes as it thickens across the animation.
     */
    public static BufferedImage[] wave(Faction f, int targetMax) {
        return CACHE.computeIfAbsent(key(f, "wave", "Wave", targetMax), k -> {
            BufferedImage[] raw = readFrames(projectile(f, "Wave"));
            if (raw.length == 0) {
                return new BufferedImage[0]; // Nairan has no Wave layer
            }
            BufferedImage[] keyed = keyAll(raw);
            return finish(keyed, contentBox(keyed, -1), targetMax);
        });
    }

    private static String key(Faction f, String layer, String name, int size) {
        return f.name() + ":" + layer + ":" + name + ":" + size;
    }

    // --- file resolution --------------------------------------------------

    /**
     * Files in a faction's layer folder, matched by prefix so {@code Engine}
     * and {@code Engines} (or {@code Shield}/{@code Shields}) both resolve.
     *
     * @param layer lowercase singular stem, e.g. "base", "engine", "weapon"
     */
    private static File[] layerDir(Faction f, String layer) {
        return DIRS.computeIfAbsent(f.name() + ":" + layer, k -> {
            File[] subs = new File(f.root()).listFiles(File::isDirectory);
            if (subs == null) {
                System.err.println("Missing art pack folder: " + f.root());
                return new File[0];
            }
            for (File d : subs) {
                if (norm(d.getName()).startsWith(layer)) {
                    File[] fs = d.listFiles(File::isFile);
                    return fs == null ? new File[0] : fs;
                }
            }
            return new File[0];
        });
    }

    /**
     * A ship's file in a layer folder. Filenames are
     * {@code <Faction> - <Ship> - <Layer>.<ext>}, so the ship is the
     * second-from-last dash-separated segment; comparing it normalised makes
     * the packs' stray double spaces and casing ("Support ship") irrelevant.
     *
     * @return null when the pack has no such layer for this ship
     */
    private static File file(Faction f, String layer, String ship) {
        String want = norm(ship);
        for (File file : layerDir(f, layer)) {
            if (norm(segment(file.getName(), 1)).equals(want)) {
                return file;
            }
        }
        return null;
    }

    /** A projectile file, named {@code <Faction> - <Projectile>.<ext>}. */
    private static File projectile(Faction f, String name) {
        String want = norm(name);
        for (File file : layerDir(f, "projectile")) {
            // Exact last-segment match, so "Bullet" never picks "Big Bullet".
            if (norm(segment(file.getName(), 0)).equals(want)) {
                return file;
            }
        }
        return null;
    }

    /** Dash-separated segment counted from the end (0 = last), extension dropped. */
    private static String segment(String filename, int fromEnd) {
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String[] parts = base.split("-");
        int i = parts.length - 1 - fromEnd;
        return i >= 0 ? parts[i] : "";
    }

    private static String norm(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    // --- boxes ------------------------------------------------------------

    /** Ship bounds (Base+Engine union), shared by the weapons overlay. */
    private static Rectangle shipBox(Faction f, String name) {
        return BOXES.computeIfAbsent(f.name() + ":ship:" + name, k -> {
            BufferedImage[] comp = shipComposite(f, name);
            if (comp.length == 0) {
                return new Rectangle(0, 0, 1, 1);
            }
            return contentBox(comp, baseLabelSkip(f, name, comp[0]));
        });
    }

    /** Bounds of a layer in its own right, with the ship's label band skipped. */
    private static Rectangle ownBox(Faction f, String name, BufferedImage[] keyed) {
        // Take the label band from the Base layer: on a destruction/shield frame
        // the effect can touch the text, merging bands and defeating detection.
        return contentBox(keyed, baseLabelSkip(f, name, keyed[0]));
    }

    private static int baseLabelSkip(Faction f, String name, BufferedImage fallback) {
        BufferedImage[] base = readFrames(file(f, "base", name));
        return labelSkip(base.length > 0 ? base[0] : fallback);
    }

    private static BufferedImage[] shipComposite(Faction f, String name) {
        BufferedImage[] base = readFrames(file(f, "base", name));
        if (base.length == 0) {
            return new BufferedImage[0];
        }
        BufferedImage[] engine = readFrames(file(f, "engine", name));
        BufferedImage[] baseKeyed = keyAll(base);
        BufferedImage[] engineKeyed = keyAll(engine);
        // Kla'ed ships have a STATIC png base, so the loop length comes from the
        // engine flicker; Nairan animates both, at matching lengths.
        int n = Math.max(baseKeyed.length, engineKeyed.length);
        BufferedImage[] comp = new BufferedImage[n];
        for (int i = 0; i < n; i++) {
            BufferedImage b = baseKeyed[i % baseKeyed.length];
            BufferedImage c = new BufferedImage(b.getWidth(), b.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = c.createGraphics();
            g.drawImage(b, 0, 0, null);
            if (engineKeyed.length > 0) {
                g.drawImage(engineKeyed[i % engineKeyed.length], 0, 0, null);
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
                int p = px[row + x];
                if (((p >>> 24) & 255) > 16 && !isBg(p & 0xFFFFFF)) {
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

    /**
     * Every frame of an image file. ImageIO's multi-image API covers both the
     * animated GIF layers and Kla'ed's single-frame PNG bases, so the pipeline
     * doesn't care which a pack uses.
     */
    private static BufferedImage[] readFrames(File f) {
        if (f == null || !f.exists()) {
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
            System.err.println("Failed to read image: " + f + " (" + e.getMessage() + ")");
            return new BufferedImage[0];
        }
    }
}
