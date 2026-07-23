package gdd;

/**
 * An enemy art pack — one per biome.
 *
 * Both packs are preview exports from the same artist, so they share a layout:
 * a folder per layer (Bases / Engine / Weapons / Shield / Destruction /
 * Projectiles), a flat {@code #2E222F} background, the ship's name baked into
 * the top of the canvas, and ships drawn facing up. Only the spellings differ
 * ({@code Engine} vs {@code Engines}, {@code Shield} vs {@code Shields}, stray
 * double spaces in the Destruction filenames) and whether the Base layer is
 * animated — Nairan ships have an animated GIF base, Kla'ed ships a static PNG.
 *
 * {@link GifSprites} resolves all of that at load time, so adding a third pack
 * is a row here rather than a code change.
 */
public enum Faction {
    NAIRAN("Nairan"),
    KLAED("Kla'ed"),
    /**
     * Biome 3. Its ships come from the Void pack, which is laid out completely
     * differently to the two gif packs (one hull with weapon-mount and damage
     * variants, as PNG spritesheets), so {@link GifSprites} finds no layers here
     * and skips it. Currently used for the backdrop only.
     */
    VOID("VoidShips");

    /** Folder under {@code src/images/}, and the prefix on every file in it. */
    public final String dir;

    Faction(String dir) {
        this.dir = dir;
    }

    public String root() {
        return "src/images/" + dir + "/";
    }
}
