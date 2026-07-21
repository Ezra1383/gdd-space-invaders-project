package gdd;

import static gdd.Global.*;

import gdd.sprite.EnemyType;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Runtime wave sequencer (Stage 5c). Implements {@link SpawnSource} so it drops
 * straight into Scene1 in place of the static list.
 *
 * The run is an endless, escalating timeline made of {@link Phase}s. Enemies
 * arrive in discrete <em>waves</em>: a wave flies in and holds, and no new wave
 * is released until the player has cleared the current one (Scene1 reports how
 * many enemies are still alive). A short breather separates waves. Independently,
 * each phase ends in a time-based <em>boss gate</em> that picks one boss from the
 * phase's hand-authored pool. After the last authored phase the Director loops
 * the final one, scaling difficulty each lap.
 *
 * Engine (this wave/phase/gate logic) and data (the phase list in
 * {@link #buildRun()}) are kept separate on purpose: Stage 6 "biome variety"
 * swaps the data, not the engine.
 */
public class Director implements SpawnSource {

    // --- Tunables (all safe to tweak) ---

    /**
     * How long each phase runs before its boss gate. Real pacing is one boss
     * every 10 minutes (10 min * 60 fps * 60 s = 36000 frames). Kept short here
     * so a gate is observable within a normal play-test — set to 36000 for real
     * play.
     */
    private static final int PHASE_FRAMES = 1500; // ~25s for testing

    /** Breather (frames) between clearing a wave and the next one arriving. */
    private static final int WAVE_GAP = 40;
    /** Wave "size budget" spent on formations; grows each wave. */
    private static final double WAVE_BASE_BUDGET = 20;
    private static final double WAVE_GROWTH = 6;      // added per wave cleared
    private static final double WAVE_BUDGET_CAP = 120;
    /** Hard ceiling on enemies per wave, so a wave never floods the screen. */
    private static final int MAX_WAVE_ENEMIES = 10;

    /** Keep formations clear of the top/bottom edges. */
    private static final int MARGIN_Y = 60;
    /** Band of columns where enemies settle and hold (right half of the board). */
    private static final int HOLD_MIN_X = BOARD_WIDTH / 2;
    private static final int HOLD_MAX_X = BOARD_WIDTH - 120;
    /** Vertical gap between enemies within a formation. */
    private static final int SPACING = 55;

    private final Random rng;
    private final List<Phase> phases;

    /** Spawns waiting for their target frame, ordered by frame. */
    private final PriorityQueue<SpawnDetails> pending =
            new PriorityQueue<>((a, b) -> Integer.compare(a.frame, b.frame));

    private int phaseIndex = 0;
    private int phaseStartFrame = 0;
    private int loops = 0;              // laps past the last authored phase

    private boolean waveActive = false;
    private int pendingWaveCount = 0;  // wave enemies not yet emitted from `pending`
    private int nextWaveFrame = 0;     // earliest frame the next wave may start
    private int waveIndex = 0;         // waves started so far (drives escalation)

    private boolean bossActive = false; // a boss fight is in progress
    private boolean bossEngaged = false; // the boss has actually appeared on-field

    public Director(Random rng) {
        this.rng = rng;
        this.phases = buildRun();
        System.out.println("[Director] start — phase: " + currentPhase().name);
    }

    @Override
    public List<SpawnDetails> poll(int frame, int aliveEnemies) {
        // Boss gate — time-based. Fires only when no boss fight is already going.
        if (!bossActive && frame - phaseStartFrame >= currentPhase().durationFrames) {
            fireBossGate(frame, currentPhase());
            bossActive = true;
            bossEngaged = false;
        }

        if (bossActive) {
            // Waves are held until the boss (and any stragglers) are cleared.
            if (aliveEnemies > 0) {
                bossEngaged = true; // boss has materialised on the field
            }
            if (bossEngaged && aliveEnemies == 0) {
                bossActive = false;
                waveActive = false;
                advancePhase(frame);           // boss down → next phase
                nextWaveFrame = frame + WAVE_GAP;
            }
        } else {
            // Normal wave sequencing.
            if (waveActive && pendingWaveCount == 0 && aliveEnemies == 0) {
                waveActive = false;
                waveIndex++;
                nextWaveFrame = frame + WAVE_GAP;
            }
            if (!waveActive && frame >= nextWaveFrame) {
                spawnWave(frame);
            }
        }

        // Emit spawns due on or before this frame.
        return drain(frame);
    }

    private Phase currentPhase() {
        return phases.get(Math.min(phaseIndex, phases.size() - 1));
    }

    private List<SpawnDetails> drain(int frame) {
        List<SpawnDetails> due = null;
        while (!pending.isEmpty() && pending.peek().frame <= frame) {
            SpawnDetails sd = pending.poll();
            if (!sd.type.startsWith("BOSS:")) {
                pendingWaveCount--; // a wave enemy has now been emitted
            }
            if (due == null) {
                due = new ArrayList<>();
            }
            due.add(sd);
        }
        return due == null ? List.of() : due;
    }

    /** Builds the next wave by spending a size budget on the phase's formations. */
    private void spawnWave(int frame) {
        Phase phase = currentPhase();
        double wb = waveBudget();
        int count = 0;
        while (count < MAX_WAVE_ENEMIES) {
            Formation f = pickAffordableFormation(phase, wb);
            if (f == null) {
                break;
            }
            wb -= f.cost;
            // Each formation is a squad of one enemy type from the phase's pool.
            EnemyType type = phase.enemyPool.get(rng.nextInt(phase.enemyPool.size()));
            List<SpawnDetails> spawns = f.build(rng, frame, type.name());
            pending.addAll(spawns);
            count += spawns.size();
        }
        if (count == 0) { // safety: never release an empty wave
            List<SpawnDetails> s = Formation.SINGLE.build(rng, frame,
                    phase.enemyPool.get(0).name());
            pending.addAll(s);
            count = s.size();
        }
        pendingWaveCount = count;
        waveActive = true;
        System.out.println("[Director] wave " + waveIndex + " (" + currentPhase().name
                + ") — " + count + " enemies");
    }

    private double waveBudget() {
        double base = WAVE_BASE_BUDGET + waveIndex * WAVE_GROWTH;
        double b = base * currentPhase().waveBudgetMult * (1.0 + 0.4 * loops);
        return Math.min(b, WAVE_BUDGET_CAP);
    }

    private void fireBossGate(int frame, Phase phase) {
        String boss = phase.bossPool.get(rng.nextInt(phase.bossPool.size()));
        System.out.println("[Director] BOSS GATE @frame " + frame + " — selected \""
                + boss + "\" from " + phase.bossPool);
        // Bosses aren't implemented yet (Stage 5+). Route through the normal
        // spawn path as a placeholder so the wiring is real and testable now.
        // (A real boss will be an enemy that blocks wave progression until killed.)
        pending.add(new SpawnDetails(frame, "BOSS:" + boss, BOARD_WIDTH, BOARD_HEIGHT / 2));
    }

    private void advancePhase(int frame) {
        phaseStartFrame = frame;
        if (phaseIndex < phases.size() - 1) {
            phaseIndex++;
        } else {
            loops++; // stay on the last phase, harder each lap
        }
        System.out.println("[Director] phase -> " + currentPhase().name
                + (loops > 0 ? " (loop " + loops + ")" : ""));
    }

    /** Weighted pick among the phase's formations affordable within `budget`. */
    private Formation pickAffordableFormation(Phase phase, double budget) {
        int totalWeight = 0;
        for (Formation f : phase.formations) {
            if (f.cost <= budget) {
                totalWeight += f.weight;
            }
        }
        if (totalWeight == 0) {
            return null;
        }
        int roll = rng.nextInt(totalWeight);
        for (Formation f : phase.formations) {
            if (f.cost <= budget) {
                roll -= f.weight;
                if (roll < 0) {
                    return f;
                }
            }
        }
        return null;
    }

    // --- Run definition (data; Stage 6 swaps this per biome) ---

    private List<Phase> buildRun() {
        List<Phase> list = new ArrayList<>();
        // Phase 1 — gentle approach: grunts and the odd darter.
        list.add(new Phase("Approach", PHASE_FRAMES, 1.0,
                List.of(Formation.SINGLE, Formation.COLUMN),
                List.of(EnemyType.GRUNT, EnemyType.DARTER),
                List.of("Warden", "Hive-Mother", "Sentinel")));
        // Phase 2 — onslaught: bigger waves, turrets join the pool.
        list.add(new Phase("Onslaught", PHASE_FRAMES, 1.4,
                List.of(Formation.SINGLE, Formation.COLUMN, Formation.WAVE, Formation.VEE),
                List.of(EnemyType.GRUNT, EnemyType.DARTER, EnemyType.TURRET),
                List.of("Leviathan", "Swarm-Lord", "Dreadnought")));
        return list;
    }

    /** One rising-intensity segment of the run, ended by a boss gate. */
    private static final class Phase {
        final String name;
        final int durationFrames;
        final double waveBudgetMult;        // scales wave size for this phase
        final List<Formation> formations;   // allowed spawn shapes this phase
        final List<EnemyType> enemyPool;    // enemy types that can appear
        final List<String> bossPool;        // one is chosen when the phase ends

        Phase(String name, int durationFrames, double waveBudgetMult,
              List<Formation> formations, List<EnemyType> enemyPool, List<String> bossPool) {
            this.name = name;
            this.durationFrames = durationFrames;
            this.waveBudgetMult = waveBudgetMult;
            this.formations = formations;
            this.enemyPool = enemyPool;
            this.bossPool = bossPool;
        }
    }

    /**
     * A spawn shape — the building block of a wave. Each builds a group of
     * {@link SpawnDetails}, some staggered across future frames. Formations are
     * about spawn <em>geometry</em>; in-flight movement/fire patterns are Stage 5.
     */
    private enum Formation {
        SINGLE(10, 40),
        COLUMN(30, 25),
        WAVE(40, 20),
        VEE(35, 12);

        final int cost;
        final int weight;

        Formation(int cost, int weight) {
            this.cost = cost;
            this.weight = weight;
        }

        List<SpawnDetails> build(Random rng, int frame, String type) {
            final int minY = MARGIN_Y;
            final int maxY = BOARD_HEIGHT - MARGIN_Y;
            // The column this formation holds at once it has flown in.
            final int home = randRange(rng, HOLD_MIN_X, HOLD_MAX_X);
            List<SpawnDetails> out = new ArrayList<>();
            switch (this) {
                case SINGLE:
                    out.add(alien(type, frame, randRange(rng, minY, maxY), home));
                    break;
                case COLUMN: {
                    int top = randRange(rng, minY, maxY - 3 * SPACING);
                    for (int i = 0; i < 4; i++) {
                        out.add(alien(type, frame, top + i * SPACING, home));
                    }
                    break;
                }
                case WAVE: {
                    boolean down = rng.nextBoolean();
                    int span = 4 * SPACING;
                    int startY = down ? randRange(rng, minY, maxY - span)
                                      : randRange(rng, minY + span, maxY);
                    int step = down ? SPACING : -SPACING;
                    for (int i = 0; i < 5; i++) {
                        // Slight diagonal hold so the wave doesn't stack in a line.
                        out.add(alien(type, frame + i * 6, startY + i * step, home + i * 8));
                    }
                    break;
                }
                case VEE: {
                    int cy = randRange(rng, minY + 2 * SPACING, maxY - 2 * SPACING);
                    out.add(alien(type, frame, cy, home - 40));          // tip forward
                    out.add(alien(type, frame + 4, cy - SPACING, home));
                    out.add(alien(type, frame + 4, cy + SPACING, home));
                    out.add(alien(type, frame + 8, cy - 2 * SPACING, home + 20));
                    out.add(alien(type, frame + 8, cy + 2 * SPACING, home + 20));
                    break;
                }
            }
            return out;
        }
    }

    private static SpawnDetails alien(String type, int frame, int y, int homeX) {
        return new SpawnDetails(frame, type, BOARD_WIDTH, y, homeX);
    }

    private static int randRange(Random rng, int lo, int hi) {
        if (hi <= lo) {
            return lo;
        }
        return lo + rng.nextInt(hi - lo + 1);
    }
}
