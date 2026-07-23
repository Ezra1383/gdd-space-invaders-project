package gdd;

import java.util.List;

/**
 * A source of enemy/powerup spawns, polled once per frame by the scene.
 *
 * Stage 3 provides a static, data-driven implementation ({@link StaticSpawnSource}).
 * Stage 4's runtime Director will implement this same interface, so it can be
 * swapped in without changing the scene's update loop.
 */
public interface SpawnSource {

    /**
     * @param frame        the current frame number
     * @param aliveEnemies how many enemies are currently on the field (lets a
     *                     wave-gated source hold the next wave until this is 0)
     * @return the spawns that should be created this frame (empty if none)
     */
    List<SpawnDetails> poll(int frame, int aliveEnemies);

    /**
     * Which biome the run is currently in. The scene watches this to swap the
     * backdrop, so the visuals follow the phase timeline without the Director
     * and the renderer having to know about each other.
     */
    default Faction biome() {
        return Faction.NAIRAN;
    }
}
