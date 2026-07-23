package gdd;

import static gdd.Global.*;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The scrolling parallax backdrop, one configuration per biome.
 *
 * Two kinds of layer:
 * <ul>
 *   <li><b>Plates</b> — full-canvas art that tiles horizontally and scrolls at
 *       its own speed. Slow plates read as distant.</li>
 *   <li><b>Props</b> — discrete objects spawned off the right edge at a random
 *       depth. Depth drives speed, scale and brightness together, so a small dim
 *       slow rock reads as genuinely far away. Because they're spawned rather
 *       than painted into a plate, the sky never visibly repeats over a
 *       ten-minute phase.</li>
 * </ul>
 *
 * Both kinds may be animated. The first two biomes use static 272x160 plates
 * upscaled 5x; the Void pack used by biome 3 is 16-frame animated 360x360
 * sheets, so plates carry frames rather than one image.
 */
public final class Background {

    /** Upscale for the 272x160 Blue/Old plates: 4x would fall short of the board. */
    private static final int PLATE_SCALE = 5;
    /** Upscale for the Void pack's 360x360 sheets — 720x720 covers the board. */
    private static final int VOID_SCALE = 2;

    /**
     * How far the broad nebula plates are darkened. The art is painted to be
     * looked at; here it's something to read enemy fire against, and its
     * brightest patches (Blue's {@code #10508B}, luminance 70) left Nairan's
     * pink Bolt at only 1.5:1 contrast. Dimming the plates while leaving stars
     * and props alone keeps the art's character and its sparkle.
     */
    private static final double NEBULA_DIM = 0.55;
    private static final double STAR_DIM = 1.0;
    /**
     * Biome 3 reads as black, with the black hole as the only thing in the sky.
     * Its base plate is a flat {@code #2E222F} — about twice as bright as Blue's
     * {@code #000418} — so it needs its own, much lower value; the surviving
     * starfield is faint, there for parallax reference rather than decoration.
     */
    private static final double VOID_BLACK_DIM = 0.12;
    private static final double VOID_STAR_DIM = 0.30;

    private static final int PROP_MIN_GAP = 150;
    private static final int PROP_MAX_GAP = 420;
    /** Depth 0 is the far distance, depth 1 is close to the camera. */
    private static final double FAR_SPEED = 0.6;
    private static final double NEAR_SPEED = 3.0;
    private static final float FAR_ALPHA = 0.45f;
    private static final float NEAR_ALPHA = 1.0f;

    private static final String BLUE = "src/images/Background/Blue Version/layered/";
    private static final String OLD = "src/images/Background/Old Version/layers/";
    private static final String VOID =
            "src/images/Background/Foozle_2DS0015_Void_EnvironmentPack/";

    /**
     * The black hole, taken from the celestial-objects sheet rather than the
     * standalone Blackhole.png. That one is a wide flat disc (300x152) at mean
     * luminance 131; this one is circular — an edge-on accretion disc cutting
     * through a dark core — and half as bright at 70, which matters because it
     * now sits centred, behind the play area rather than off to one side.
     * The trade is that it's a single static image where the other animates.
     */
    private static final String CELESTIAL =
            "src/images/Background/BlackHole/CelestialObjects.png";
    private static final Rectangle BLACK_HOLE_CELL = new Rectangle(256, 1, 128, 94);
    /** 128x94 at 5x is 640x470 — dominant on a 716x700 board without touching the edges. */
    private static final int BLACK_HOLE_SCALE = 5;
    /**
     * Centring it puts the accretion disc's left arm straight across the
     * player's dodging zone, at the altitude they spend most time at. At full
     * strength that band measured luminance 48 — against 13 and 18 for the worst
     * band in the other two biomes — dropping bullet contrast there to 3.5:1.
     * The hole is the only thing in this sky, so it still dominates at this
     * brightness; raise it and that stripe is what suffers.
     */
    private static final double BLACK_HOLE_DIM = 0.40;

    /** Rotations pre-rendered per wreck; they double as its tumble animation. */
    private static final int TUMBLE_STEPS = 12;
    private static final int TUMBLE_TICKS = 14;

    private static final Map<String, BufferedImage[]> LAYERS = new ConcurrentHashMap<>();

    /** Purely cosmetic, so it never touches the seeded gameplay streams. */
    private final Random rng = new Random();

    private final List<Plate> plates;
    private final List<PropDef> propDefs;
    private final int propWeightTotal;
    private final List<Prop> props = new ArrayList<>();
    private final Feature feature; // nullable: a fixed, dominant backdrop object
    private int nextProp;

    private Background(List<Plate> plates, List<PropDef> propDefs) {
        this(plates, propDefs, null);
    }

    private Background(List<Plate> plates, List<PropDef> propDefs, Feature feature) {
        this.plates = plates;
        this.propDefs = propDefs;
        this.feature = feature;
        int total = 0;
        for (PropDef p : propDefs) {
            total += p.weight;
        }
        this.propWeightTotal = Math.max(1, total);
        this.nextProp = randGap();
        for (int i = 0; i < 4; i++) {
            spawnProp(rng.nextInt(BOARD_WIDTH)); // start with a populated sky
        }
    }

    /**
     * Scales every biome's plates up front. Upscaling costs ~20ms a plate, which
     * would otherwise land as a dropped frame when the run crosses into a new
     * biome. Safe to call off the EDT; the cache is concurrent.
     */
    public static void preload() {
        for (Faction f : Faction.values()) {
            Background b = of(f);
            for (Plate p : b.plates) {
                p.frames();
            }
            if (b.feature != null) {
                b.feature.frames();
            }
        }
    }

    /**
     * Builds the biome-3 wreck props. Separate from {@link #preload()} because
     * these derive from the ship packs, so this must run after those decode.
     */
    public static void preloadWrecks() {
        for (PropDef d : of(Faction.VOID).propDefs) {
            d.frames();
        }
    }

    public static Background of(Faction faction) {
        switch (faction) {
            case KLAED:
                return new Background(
                        List.of(plate(OLD + "parallax-space-backgound.png", 0.15, NEBULA_DIM),
                                plate(OLD + "parallax-space-far-planets.png", 0.30, NEBULA_DIM),
                                plate(OLD + "parallax-space-stars.png", 0.50, STAR_DIM)),
                        List.of(prop(OLD + "parallax-space-big-planet.png", 2, 3, 2),
                                prop(OLD + "parallax-space-ring-planet.png", 2, 3, 2)));
            case VOID:
                return voidBiome();
            default:
                // Blue has no mid plate, so its small planet prop covers that depth.
                return new Background(
                        List.of(plate(BLUE + "blue-back.png", 0.15, NEBULA_DIM),
                                plate(BLUE + "blue-stars.png", 0.50, STAR_DIM)),
                        List.of(prop(BLUE + "asteroid-1.png", 2, 3, 4),
                                prop(BLUE + "asteroid-2.png", 2, 3, 4),
                                prop(BLUE + "prop-planet-small.png", 2, 4, 3),
                                prop(BLUE + "prop-planet-big.png", 2, 3, 2)));
        }
    }

    /**
     * Biome 3 — the decay of a civilisation.
     *
     * The Void pack is all natural objects: stars, a black hole, an asteroid and
     * a <em>living</em> Earth. On its own that reads as emptiness, not ruin, so
     * two things are done to it here. The planet is remapped to a dead world —
     * a recognisable Earth with grey oceans and ash continents carries the theme
     * better than any amount of nebula. And the debris is the hulls of the two
     * fleets already beaten: biome 3 is their graveyard, which no purchased pack
     * could have supplied.
     *
     * The pack's star layers don't tile (wrap seam 4.6-25.9 against an interior
     * baseline near 0), so plates here are mirror-tiled instead.
     */
    private static Background voidBiome() {
        // A black sky. The base plate is pushed to near-black and the denser of
        // the two star layers is dropped entirely; what's left is the sparse one
        // (97% transparent) at low brightness. That keeps a faint drifting
        // reference so the parallax still reads as movement — with nothing
        // scrolling at all, a flat black field looks static however fast the
        // props travel. Drop this plate too for a literally empty sky.
        List<Plate> plates = List.of(
                sheetPlate(VOID + "Backgrounds/PNGs/Condesed/Starry background  - Layer 01 - Void.png",
                        0.10, VOID_BLACK_DIM),
                sheetPlate(VOID + "Backgrounds/PNGs/Condesed/Starry background  - Layer 02 - Stars.png",
                        0.35, VOID_STAR_DIM));

        List<PropDef> props = new ArrayList<>();
        props.add(new PropDef("void:asteroid", 1, 3, 5, 0,
                () -> crop(sheet(VOID + "Asteroids/PNGs/Asteroid 01 - Base.png", 96))));
        // Wreckage: the fleets from biomes 1 and 2, cold and tumbling.
        props.add(wreck(Faction.NAIRAN, "Fighter", 3));
        props.add(wreck(Faction.NAIRAN, "Frigate", 3));
        props.add(wreck(Faction.NAIRAN, "Battlecruiser", 2));
        props.add(wreck(Faction.KLAED, "Fighter", 3));
        props.add(wreck(Faction.KLAED, "Bomber", 3));
        props.add(wreck(Faction.KLAED, "Battlecruiser", 2));

        // The black hole: one fixed object, dead centre, and the only celestial
        // body in the biome. Everything else here is wreckage.
        Feature hole = new Feature("void:blackhole", 0, 0.5,
                () -> new BufferedImage[]{
                        dim(scale(sub(CELESTIAL, BLACK_HOLE_CELL), BLACK_HOLE_SCALE),
                                BLACK_HOLE_DIM)});
        return new Background(plates, props, hole);
    }

    // --- layer construction ----------------------------------------------

    private static Plate plate(String path, double speed, double dim) {
        return new Plate(path + "@" + PLATE_SCALE + "x" + dim, speed, false, 0,
                () -> new BufferedImage[]{
                        dim(Images.pixelScaled(path, PLATE_SCALE), dim)});
    }

    /**
     * One frame of a sheet layer, scrolled. Mirror-tiled, since the Void plates
     * don't wrap.
     *
     * The sheet's 16 frames are deliberately <em>not</em> played. They aren't a
     * pan or a brightness twinkle — measured against each other, the best-fit
     * horizontal shift is random and barely beats no shift at all, because
     * different stars are lit in each frame. Played back while the plate also
     * scrolls, stars wink out in one place and reappear in another, which reads
     * as the starfield teleporting. Scrolling a single frame gives the steady
     * parallax the other two biomes have.
     */
    private static Plate sheetPlate(String path, double speed, double dim) {
        return new Plate(path + "@" + VOID_SCALE + "x" + dim, speed, true, 0,
                () -> {
                    // One frame, not a composite of all 16: the frames light
                    // *different* stars rather than dimming the same ones, so
                    // flattening them stacks 16 starfields and the sky comes out
                    // far too bright (measured mean luminance 60 against 24 for
                    // a single frame, where the other biomes sit near 13).
                    // Keeping one image also avoids ~33MB per layer of scaled
                    // frames that nothing would ever draw.
                    BufferedImage[] f = sheet(path, 360);
                    return new BufferedImage[]{dim(scale(f[0], VOID_SCALE), dim)};
                });
    }

    private static PropDef prop(String path, int minScale, int maxScale, int weight) {
        return new PropDef(path, minScale, maxScale, weight, 0,
                () -> new BufferedImage[]{Images.load(path)});
    }

    /**
     * A dead hull from an earlier biome: desaturated, darkened, and pre-rendered
     * at a ring of angles that double as a slow tumble.
     */
    private static PropDef wreck(Faction faction, String ship, int weight) {
        return new PropDef("wreck:" + faction + ":" + ship, 1, 1, weight, TUMBLE_TICKS,
                () -> {
                    BufferedImage[] live = GifSprites.ship(faction, ship, 72);
                    if (live.length == 0) {
                        return new BufferedImage[0];
                    }
                    BufferedImage dead = derelict(live[0]);
                    BufferedImage[] out = new BufferedImage[TUMBLE_STEPS];
                    for (int i = 0; i < TUMBLE_STEPS; i++) {
                        out[i] = rotate(dead, i * 2 * Math.PI / TUMBLE_STEPS);
                    }
                    return out;
                });
    }

    // --- per-frame --------------------------------------------------------

    public void update() {
        for (Plate p : plates) {
            p.scroll += p.speed;
            p.advance();
        }
        if (feature != null) {
            feature.advance();
        }
        for (Prop p : props) {
            p.x -= p.speed;
            p.advance();
        }
        props.removeIf(p -> p.x + p.width() < 0);
        if (--nextProp <= 0) {
            nextProp = randGap();
            spawnProp(BOARD_WIDTH);
        }
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        for (Plate p : plates) {
            drawTiled(g2, p.current(), p.scroll, p.mirror);
        }
        // Between plates and props, so debris drifts in front of it.
        if (feature != null) {
            BufferedImage f = feature.current();
            if (f != null) {
                g2.drawImage(f, (int) (BOARD_WIDTH * feature.anchorX) - f.getWidth() / 2,
                        (BOARD_HEIGHT - f.getHeight()) / 2, null);
            }
        }
        Composite old = g2.getComposite();
        for (Prop p : props) {
            BufferedImage img = p.current();
            if (img == null) {
                continue;
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha));
            g2.drawImage(img, (int) Math.round(p.x), p.y, null);
        }
        g2.setComposite(old);
    }

    /**
     * Repeats a plate across the board, cropped to the middle of its height.
     * Mirrored plates alternate with a flipped copy, so art that doesn't wrap
     * still tiles without a visible seam.
     */
    private void drawTiled(Graphics2D g2, BufferedImage plate, double scroll, boolean mirror) {
        if (plate == null) {
            return;
        }
        int w = plate.getWidth();
        int h = plate.getHeight();
        int period = mirror ? w * 2 : w;
        int y = (BOARD_HEIGHT - h) / 2; // negative: crops evenly top and bottom
        int off = (int) Math.floor(((scroll % period) + period) % period);
        for (int x = -off; x < BOARD_WIDTH; x += period) {
            g2.drawImage(plate, x, y, null);
            if (mirror) {
                // Destination x reversed => drawn flipped, so the edges match.
                g2.drawImage(plate, x + 2 * w, y, x + w, y + h, 0, 0, w, h, null);
            }
        }
    }

    private void spawnProp(int atX) {
        if (propDefs.isEmpty()) {
            return;
        }
        PropDef def = pickDef();
        // Singular objects: one dead homeworld, one black hole. Two of either
        // on screen at once undoes the story they're there to tell.
        if (def.unique && alive(def.key)) {
            return;
        }
        BufferedImage[] frames = def.frames();
        if (frames.length == 0) {
            return;
        }
        double depth = rng.nextDouble();
        int scale = def.minScale + (int) Math.round(depth * (def.maxScale - def.minScale));
        Prop p = new Prop();
        p.key = def.key;
        p.frames = scale > 1 ? scaledSet(def.key, scale, frames) : frames;
        p.animTicks = def.animTicks;
        p.idx = rng.nextInt(p.frames.length);
        p.x = atX;
        int h = p.frames[0].getHeight();
        p.y = rng.nextInt(Math.max(1, BOARD_HEIGHT - h / 2)) - h / 4;
        p.speed = FAR_SPEED + depth * (NEAR_SPEED - FAR_SPEED);
        p.alpha = (float) (FAR_ALPHA + depth * (NEAR_ALPHA - FAR_ALPHA));
        props.add(p);
    }

    private boolean alive(String key) {
        for (Prop p : props) {
            if (key.equals(p.key)) {
                return true;
            }
        }
        return false;
    }

    private PropDef pickDef() {
        int roll = rng.nextInt(propWeightTotal);
        for (PropDef d : propDefs) {
            roll -= d.weight;
            if (roll < 0) {
                return d;
            }
        }
        return propDefs.get(0);
    }

    private int randGap() {
        return PROP_MIN_GAP + rng.nextInt(PROP_MAX_GAP - PROP_MIN_GAP + 1);
    }

    // --- image helpers ----------------------------------------------------

    private static BufferedImage[] layer(String key, Supplier<BufferedImage[]> make) {
        return LAYERS.computeIfAbsent(key, k -> make.get());
    }

    private static BufferedImage[] scaledSet(String key, int factor, BufferedImage[] src) {
        return layer(key + "@" + factor, () -> {
            BufferedImage[] out = new BufferedImage[src.length];
            for (int i = 0; i < src.length; i++) {
                out[i] = scale(src[i], factor);
            }
            return out;
        });
    }

    /** One object cut out of a multi-object sheet. */
    private static BufferedImage sub(String path, Rectangle cell) {
        return Images.load(path).getSubimage(cell.x, cell.y, cell.width, cell.height);
    }

    /** Slices a horizontal spritesheet into equal frames. */
    private static BufferedImage[] sheet(String path, int frameW) {
        BufferedImage s = Images.load(path);
        int n = Math.max(1, s.getWidth() / frameW);
        BufferedImage[] out = new BufferedImage[n];
        for (int i = 0; i < n; i++) {
            out[i] = s.getSubimage(i * frameW, 0, Math.min(frameW, s.getWidth() - i * frameW),
                    s.getHeight());
        }
        return out;
    }

    /** Crops every frame to the union of their visible pixels. */
    private static BufferedImage[] crop(BufferedImage[] frames) {
        int minx = Integer.MAX_VALUE;
        int miny = Integer.MAX_VALUE;
        int maxx = -1;
        int maxy = -1;
        for (BufferedImage f : frames) {
            int w = f.getWidth();
            int h = f.getHeight();
            int[] px = f.getRGB(0, 0, w, h, null, 0, w);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (((px[y * w + x] >>> 24) & 255) > 16) {
                        minx = Math.min(minx, x);
                        maxx = Math.max(maxx, x);
                        miny = Math.min(miny, y);
                        maxy = Math.max(maxy, y);
                    }
                }
            }
        }
        if (maxx < 0) {
            return frames;
        }
        Rectangle box = new Rectangle(minx, miny, maxx - minx + 1, maxy - miny + 1);
        BufferedImage[] out = new BufferedImage[frames.length];
        for (int i = 0; i < frames.length; i++) {
            out[i] = frames[i].getSubimage(box.x, box.y, box.width, box.height);
        }
        return out;
    }

    private static BufferedImage scale(BufferedImage src, int factor) {
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

    /** Scales RGB down, leaving alpha alone so layer shapes are unchanged. */
    private static BufferedImage dim(BufferedImage src, double f) {
        if (f >= 1.0) {
            return src;
        }
        return map(src, (r, g, b) -> new int[]{(int) (r * f), (int) (g * f), (int) (b * f)});
    }

    /** A cold, spent hull: nearly all colour drained, then heavily darkened. */
    private static BufferedImage derelict(BufferedImage src) {
        return map(src, (r, g, b) -> {
            double grey = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            double keep = 0.15; // a trace of the original faction colour
            return new int[]{
                    (int) ((r * keep + grey * (1 - keep)) * 0.45),
                    (int) ((g * keep + grey * (1 - keep)) * 0.45),
                    (int) ((b * keep + grey * (1 - keep)) * 0.48)};
        });
    }

    private interface Recolour {
        int[] apply(int r, int g, int b);
    }

    private static BufferedImage map(BufferedImage src, Recolour fn) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            if (((p >>> 24) & 255) == 0) {
                continue;
            }
            int[] c = fn.apply((p >> 16) & 255, (p >> 8) & 255, p & 255);
            px[i] = (p & 0xFF000000)
                    | (Math.min(255, Math.max(0, c[0])) << 16)
                    | (Math.min(255, Math.max(0, c[1])) << 8)
                    | Math.min(255, Math.max(0, c[2]));
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    // --- types ------------------------------------------------------------

    /** A full-canvas layer that tiles horizontally, optionally animated. */
    private static final class Plate {
        final String key;
        final double speed;
        final boolean mirror;
        final int animTicks;
        final Supplier<BufferedImage[]> source;
        double scroll;
        int tick;
        int idx;

        Plate(String key, double speed, boolean mirror, int animTicks,
              Supplier<BufferedImage[]> source) {
            this.key = key;
            this.speed = speed;
            this.mirror = mirror;
            this.animTicks = animTicks;
            this.source = source;
        }

        BufferedImage[] frames() {
            return layer(key, source);
        }

        BufferedImage current() {
            BufferedImage[] f = frames();
            return f.length == 0 ? null : f[idx % f.length];
        }

        void advance() {
            if (animTicks > 0 && ++tick >= animTicks) {
                tick = 0;
                idx++;
            }
        }
    }

    /**
     * A single fixed, dominant backdrop object — it neither scrolls nor drifts,
     * because it's meant to be the thing the biome is *about* rather than
     * scenery passing by.
     */
    private static final class Feature {
        final String key;
        final int animTicks;
        /** Where its centre sits, as a fraction of board width. 0.5 is centred. */
        final double anchorX;
        final Supplier<BufferedImage[]> source;
        int tick;
        int idx;

        Feature(String key, int animTicks, double anchorX, Supplier<BufferedImage[]> source) {
            this.key = key;
            this.animTicks = animTicks;
            this.anchorX = anchorX;
            this.source = source;
        }

        BufferedImage[] frames() {
            return layer(key, source);
        }

        BufferedImage current() {
            BufferedImage[] f = frames();
            return f.length == 0 ? null : f[idx % f.length];
        }

        void advance() {
            if (animTicks > 0 && ++tick >= animTicks) {
                tick = 0;
                idx++;
            }
        }
    }

    /** A spawnable background object and the depth range it may appear at. */
    private static final class PropDef {
        final String key;
        final int minScale;
        final int maxScale;
        final int weight;
        final int animTicks;
        /** Only ever one of these on screen at a time. */
        final boolean unique;
        final Supplier<BufferedImage[]> source;

        PropDef(String key, int minScale, int maxScale, int weight, int animTicks,
                Supplier<BufferedImage[]> source) {
            this(key, minScale, maxScale, weight, animTicks, false, source);
        }

        PropDef(String key, int minScale, int maxScale, int weight, int animTicks,
                boolean unique, Supplier<BufferedImage[]> source) {
            this.key = key;
            this.minScale = minScale;
            this.maxScale = maxScale;
            this.weight = weight;
            this.animTicks = animTicks;
            this.unique = unique;
            this.source = source;
        }

        BufferedImage[] frames() {
            return layer(key, source);
        }
    }

    private static final class Prop {
        String key;
        BufferedImage[] frames;
        int animTicks;
        int idx;
        int tick;
        double x;
        int y;
        double speed;
        float alpha;

        BufferedImage current() {
            return frames.length == 0 ? null : frames[idx % frames.length];
        }

        int width() {
            return frames.length == 0 ? 0 : frames[0].getWidth();
        }

        void advance() {
            if (animTicks > 0 && ++tick >= animTicks) {
                tick = 0;
                idx++;
            }
        }
    }
}
