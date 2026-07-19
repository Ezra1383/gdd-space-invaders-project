package gdd;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

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

    private static int dist(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        int db = (a & 255) - (b & 255);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
