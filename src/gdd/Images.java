package gdd;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NodeList;

/**
 * Robust image loading. Decodes with {@link ImageIO} (synchronous, into a
 * BufferedImage) and scales by drawing — avoiding AWT's Toolkit fetch/scale
 * pipeline, which throws {@code ClassCastException [I -> [B} on certain PNGs
 * (e.g. explosion.png, bomb.png) when scaled with SCALE_SMOOTH.
 */
public final class Images {

    private Images() {
    }

    public static BufferedImage load(String path) {
        try {
            BufferedImage img = ImageIO.read(new File(path));
            if (img != null) {
                return img;
            }
        } catch (Exception e) {
            // fall through to placeholder
        }
        System.err.println("Failed to load image: " + path);
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }

    public static BufferedImage scaledBy(String path, int factor) {
        BufferedImage src = load(path);
        return scale(src, src.getWidth() * factor, src.getHeight() * factor);
    }

    /**
     * Loads an image, multiplies its RGB channels by the given tint (alpha
     * preserved), and scales it by a floating factor. Used to spin enemy-type
     * variants off a single base sprite (e.g. alien.png recoloured per type).
     */
    public static BufferedImage scaledTinted(String path, double factor,
                                             double tr, double tg, double tb) {
        BufferedImage src = load(path);
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 255;
                int r = Math.min(255, (int) (((argb >> 16) & 255) * tr));
                int g = Math.min(255, (int) (((argb >> 8) & 255) * tg));
                int b = Math.min(255, (int) ((argb & 255) * tb));
                tinted.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return scale(tinted, Math.max(1, (int) Math.round(w * factor)),
                Math.max(1, (int) Math.round(h * factor)));
    }

    /**
     * Integer upscale with nearest-neighbour, keeping pixel art crisp. Use this
     * for source art authored at a small native size (the 272x160 background
     * plates); {@link #scaledBy} smooths, which turns pixel art to mush.
     */
    public static BufferedImage pixelScaled(String path, int factor) {
        BufferedImage src = load(path);
        int w = src.getWidth() * factor;
        int h = src.getHeight() * factor;
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    /**
     * Blends an image toward a red "corrupted" version by {@code t} (0 = the
     * original, 1 = fully red). The red version keeps the sprite's luminance
     * structure but recolours it into Nemesis's palette, so the player's blue
     * jet morphs cleanly into the red one as corruption climbs.
     */
    public static BufferedImage tintTowardRed(BufferedImage src, double t) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 255;
                if (a == 0) {
                    continue;
                }
                int r = (argb >> 16) & 255;
                int g = (argb >> 8) & 255;
                int b = argb & 255;
                double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                int rr = (int) Math.min(255, 70 + lum * 1.1);
                int rg = (int) (lum * 0.30);
                int rb = (int) (lum * 0.25);
                int nr = (int) Math.round(r + (rr - r) * t);
                int ng = (int) Math.round(g + (rg - g) * t);
                int nb = (int) Math.round(b + (rb - b) * t);
                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    /**
     * Mirrors an image left-to-right. The player's jet is drawn facing right;
     * the mirror-match boss wears the same art facing back at them.
     */
    public static BufferedImage flippedH(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, w, 0, 0, h, 0, 0, w, h, null); // destination x reversed
        g.dispose();
        return dst;
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(Math.max(1, w), Math.max(1, h),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    // --- Sprite-sheet extraction (Stage: real art) ---

    private static BufferedImage sheetCache;
    private static String sheetPath;

    private static BufferedImage sheet(String path) {
        if (sheetCache == null || !path.equals(sheetPath)) {
            sheetCache = load(path);
            sheetPath = path;
        }
        return sheetCache;
    }

    /**
     * Cuts a sprite from a sheet, keys the given background colours to
     * transparent, and scales it nearest-neighbour (crisp pixel art).
     *
     * @param keys background colours (0xRRGGBB) to make transparent
     * @param tol  colour-distance tolerance for keying
     */
    public static BufferedImage tile(String path, int x, int y, int w, int h,
                                     int scale, int[] keys, int tol) {
        BufferedImage src = sheet(path).getSubimage(x, y, w, h);
        BufferedImage cut = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int rgb = src.getRGB(xx, yy) & 0xFFFFFF;
                boolean bg = false;
                for (int k : keys) {
                    if (dist(rgb, k) <= tol) {
                        bg = true;
                        break;
                    }
                }
                cut.setRGB(xx, yy, bg ? 0x00000000 : (0xFF000000 | rgb));
            }
        }
        BufferedImage dst = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(cut, 0, 0, w * scale, h * scale, null);
        g.dispose();
        return dst;
    }

    /**
     * Cuts a horizontal animation strip (frames laid left-to-right) into its
     * frames, keeping each frame's existing alpha, and nearest-neighbour scales
     * them. Unlike {@link #tile}, this does no colour-keying — use it for art
     * that already ships with a transparent (RGBA) background, like the Offense
     * pickup spin strips.
     */
    public static BufferedImage[] strip(String path, int frameW, int frameH, int scale) {
        BufferedImage src = load(path);
        int count = Math.max(1, src.getWidth() / frameW);
        BufferedImage[] out = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            BufferedImage cell = src.getSubimage(i * frameW, 0, frameW, frameH);
            BufferedImage dst = new BufferedImage(frameW * scale, frameH * scale,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(cell, 0, 0, frameW * scale, frameH * scale, null);
            g.dispose();
            out[i] = dst;
        }
        return out;
    }

    /**
     * Decodes an animated GIF into full-canvas frames, then keeps an even sample
     * of at most {@code maxFrames} scaled to {@code targetH} px tall (bilinear).
     *
     * ImageIO returns each GIF frame as only the sub-rectangle that changed, at
     * its own offset — drawing those raw would tear. This reassembles them onto a
     * persistent canvas honouring each frame's offset and disposal, so a heavily
     * optimised backdrop (like biome 3's 192-frame black hole) plays correctly
     * while only a handful of composited frames are retained.
     */
    public static BufferedImage[] gifFramesScaled(String path, int maxFrames, int targetH) {
        File file = new File(path);
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) {
                return new BufferedImage[0];
            }
            ImageReader r = it.next();
            r.setInput(in);
            int n = r.getNumImages(true);
            if (n <= 0) {
                return new BufferedImage[0];
            }
            BufferedImage first = r.read(0);
            int cw = first.getWidth();
            int ch = first.getHeight();
            BufferedImage canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            int step = Math.max(1, n / Math.max(1, maxFrames));
            double s = targetH / (double) ch;
            int tw = Math.max(1, (int) Math.round(cw * s));
            int th = Math.max(1, (int) Math.round(ch * s));
            List<BufferedImage> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                BufferedImage frame = (i == 0) ? first : r.read(i);
                int[] meta = gifFrameMeta(r.getImageMetadata(i));
                g.drawImage(frame, meta[0], meta[1], null);
                if (i % step == 0) {
                    BufferedImage snap = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D sg = snap.createGraphics();
                    sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    sg.drawImage(canvas, 0, 0, tw, th, null);
                    sg.dispose();
                    out.add(snap);
                }
                if (meta[2] == 2) { // restoreToBackgroundColor: clear this frame's rect
                    java.awt.Composite oc = g.getComposite();
                    g.setComposite(java.awt.AlphaComposite.Clear);
                    g.fillRect(meta[0], meta[1], frame.getWidth(), frame.getHeight());
                    g.setComposite(oc);
                }
            }
            g.dispose();
            r.dispose();
            return out.toArray(new BufferedImage[0]);
        } catch (Exception e) {
            System.err.println("Failed to read gif: " + path + " (" + e.getMessage() + ")");
            return new BufferedImage[0];
        }
    }

    /** GIF frame {left, top, disposal} — disposal 2 == restoreToBackgroundColor. */
    private static int[] gifFrameMeta(IIOMetadata md) {
        int left = 0;
        int top = 0;
        int disposal = 0;
        try {
            IIOMetadataNode root =
                    (IIOMetadataNode) md.getAsTree("javax_imageio_gif_image_1.0");
            NodeList d = root.getElementsByTagName("ImageDescriptor");
            if (d.getLength() > 0) {
                IIOMetadataNode nd = (IIOMetadataNode) d.item(0);
                left = parseIntSafe(nd.getAttribute("imageLeftPosition"));
                top = parseIntSafe(nd.getAttribute("imageTopPosition"));
            }
            NodeList gce = root.getElementsByTagName("GraphicControlExtension");
            if (gce.getLength() > 0
                    && "restoreToBackgroundColor".equals(
                            ((IIOMetadataNode) gce.item(0)).getAttribute("disposalMethod"))) {
                disposal = 2;
            }
        } catch (Exception ignore) {
            // fall back to {0,0,0}: draw at origin, accumulate
        }
        return new int[] {left, top, disposal};
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int dist(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        int db = (a & 255) - (b & 255);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
