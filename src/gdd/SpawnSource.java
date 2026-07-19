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
     * @param frame the current frame number
     * @return the spawns that should be created this frame (empty if none)
     */
    List<SpawnDetails> poll(int frame);
}
