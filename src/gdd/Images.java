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
}
