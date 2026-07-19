package gdd;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SpawnSource} backed by a fixed, pre-authored list of spawns.
 *
 * The list is sorted by frame and walked with an advancing index pointer, so
 * polling is O(1) amortized per frame (the scene calls poll() for every frame
 * in order). Multiple spawns may share the same frame — they are all returned
 * together.
 */
public class StaticSpawnSource implements SpawnSource {

    private final List<SpawnDetails> spawns;
    private int nextIndex = 0;

    public StaticSpawnSource(List<SpawnDetails> spawns) {
        this.spawns = new ArrayList<>(spawns);
        // Stable sort by frame so authoring order can be arbitrary, while spawns
        // sharing a frame keep their listed order.
        this.spawns.sort((a, b) -> Integer.compare(a.frame, b.frame));
    }

    @Override
    public List<SpawnDetails> poll(int frame, int aliveEnemies) {
        List<SpawnDetails> due = null;
        // <= (not ==) so a frame the scene never lands on exactly still flushes.
        while (nextIndex < spawns.size() && spawns.get(nextIndex).frame <= frame) {
            if (due == null) {
                due = new ArrayList<>();
            }
            due.add(spawns.get(nextIndex));
            nextIndex++;
        }
        return due == null ? List.of() : due;
    }
}
