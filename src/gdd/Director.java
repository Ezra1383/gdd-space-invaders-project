package gdd;

import static gdd.Global.*;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Runtime spawn generator (Stage 4). Implements {@link SpawnSource} so it drops
 * straight into Scene1 in place of the static list, with no change to the scene
 * loop.
 *
 * The run is an endless, escalating timeline made of {@link Phase}s. Within a
 * phase a difficulty <em>budget</em> accrues over time and is spent on spawn
 * {@link Formation}s drawn from the phase's allowed set. Each phase ends in a
 * <em>boss gate</em>: the RNG picks one boss from that phase's hand-authored
 * pool. After the last authored phase the Director loops the final one, scaling
 * difficulty each lap, so bosses keep arriving forever.
 *
 * Engine (this budget/phase/gate logic) and data (the phase list in
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

    /** Ceiling on banked budget, so a boss-quiet lull doesn't hoard a flood. */
    private static final double BUDGET_CAP = 80;

    /** Keep formations clear of the top/bottom edges. */
    private static final int MARGIN_Y = 60;
    /** Band of columns where enemies settle and hold (right half of the board). */
    private static final int HOLD_MIN_X = BOARD_WIDTH / 2;
    private static final int HOLD_MAX_X = BOARD_WIDTH - 120;
    /** Vertical gap between enemies within a formation. */
    private static final int SPACING = 55;
    /** Brief calm after a boss gate fires. */
    private static final int BOSS_QUIET_FRAMES = 120;

    private final Random rng;
    private final List<Phase> phases;

    /** Spawns waiting for their target frame, ordered by frame. */
    private final PriorityQueue<SpawnDetails> pending =
            new PriorityQueue<>((a, b) -> Integer.compare(a.frame, b.frame));

    private int phaseIndex = 0;
    private int phaseStartFrame = 0;
    private int loops = 0;              // laps past the last authored phase
    private double budget = 0;
    private int lastSpawnFrame = -10000;
    private int quietUntilFrame = 0;

    public Director(Random rng) {
        this.rng = rng;
        this.phases = buildRun();
        System.out.println("[Director] start — phase: " + currentPhase().name);
    }

    @Override
    public List<SpawnDetails> poll(int frame) {
        Phase phase = currentPhase();

        // 1. Boss gate — has this phase's time elapsed?
        if (frame - phaseStartFrame >= phase.durationFrames) {
            fireBossGate(frame, phase);
            advancePhase(frame);
            phase = currentPhase();
        }

        // 2. Grow the difficulty budget. The rate rises within the phase (a
        //    crescendo toward the boss) and across loops (endless escalation).
        double phaseProgress = (double) (frame - phaseStartFrame) / phase.durationFrames;
        budget += phase.budgetPerFrame * (1.0 + phaseProgress) * (1.0 + 0.5 * loops);
        budget = Math.min(budget, BUDGET_CAP);

        // 3. Maybe emit a formation, if off cooldown and past any boss quiet time.
        if (frame >= quietUntilFrame && frame - lastSpawnFrame >= phase.minSpawnGap) {
            Formation f = pickAffordableFormation(phase);
            if (f != null) {
                budget -= f.cost;
                lastSpawnFrame = frame;
                pending.addAll(f.build(rng, frame));
            }
        }

        // 4. Drain everything due on or before this frame.
        return drain(frame);
    }

    private Phase currentPhase() {
        return phases.get(Math.min(phaseIndex, phases.size() - 1));
    }

    private List<SpawnDetails> drain(int frame) {
        List<SpawnDetails> due = null;
        while (!pending.isEmpty() && pending.peek().frame <= frame) {
            if (due == null) {
                due = new ArrayList<>();
            }
            due.add(pending.poll());
        }
        return due == null ? List.of() : due;
    }

    private void fireBossGate(int frame, Phase phase) {
        String boss = phase.bossPool.get(rng.nextInt(phase.bossPool.size()));
        System.out.println("[Director] BOSS GATE @frame " + frame + " — selected \""
                + boss + "\" from " + phase.bossPool);
        // Bosses aren't implemented yet (Stage 5+). Route through the normal
        // spawn path as a placeholder so the wiring is real and testable now.
        pending.add(new SpawnDetails(frame, "BOSS:" + boss, BOARD_WIDTH, BOARD_HEIGHT / 2));
        quietUntilFrame = frame + BOSS_QUIET_FRAMES;
    }

    private void advancePhase(int frame) {
        phaseStartFrame = frame;
        budget = 0;
        if (phaseIndex < phases.size() - 1) {
            phaseIndex++;
        } else {
            loops++; // stay on the last phase, harder each lap
        }
        System.out.println("[Director] phase -> " + currentPhase().name
                + (loops > 0 ? " (loop " + loops + ")" : ""));
    }

    /** Weighted pick among the phase's formations we can currently afford. */
    private Formation pickAffordableFormation(Phase phase) {
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
        // Phase 1 — gentle approach: singles, some columns, the odd powerup.
        list.add(new Phase("Approach", PHASE_FRAMES, 0.6, 45,
                List.of(Formation.SINGLE, Formation.COLUMN, Formation.POWERUP),
                List.of("Warden", "Hive-Mother", "Sentinel")));
        // Phase 2 — onslaught: denser, adds waves and V-formations.
        list.add(new Phase("Onslaught", PHASE_FRAMES, 1.0, 35,
                List.of(Formation.SINGLE, Formation.COLUMN, Formation.WAVE,
                        Formation.VEE, Formation.POWERUP),
                List.of("Leviathan", "Swarm-Lord", "Dreadnought")));
        return list;
    }

    /** One rising-intensity segment of the run, ended by a boss gate. */
    private static final class Phase {
        final String name;
        final int durationFrames;
        final double budgetPerFrame;
        final int minSpawnGap;              // min frames between formations
        final List<Formation> formations;   // allowed spawn shapes this phase
        final List<String> bossPool;        // one is chosen when the phase ends

        Phase(String name, int durationFrames, double budgetPerFrame, int minSpawnGap,
              List<Formation> formations, List<String> bossPool) {
            this.name = name;
            this.durationFrames = durationFrames;
            this.budgetPerFrame = budgetPerFrame;
            this.minSpawnGap = minSpawnGap;
            this.formations = formations;
            this.bossPool = bossPool;
        }
    }

    /**
     * A spawn shape — the unit of the Director's output. Each builds a group of
     * {@link SpawnDetails}, some staggered across future frames. Formations are
     * about spawn <em>geometry</em>; in-flight movement/fire patterns are Stage 5.
     */
    private enum Formation {
        SINGLE(10, 40),
        COLUMN(30, 25),
        WAVE(40, 20),
        VEE(35, 12),
        POWERUP(15, 10);

        final int cost;
        final int weight;

        Formation(int cost, int weight) {
            this.cost = cost;
            this.weight = weight;
        }

        List<SpawnDetails> build(Random rng, int frame) {
            final int minY = MARGIN_Y;
            final int maxY = BOARD_HEIGHT - MARGIN_Y;
            // The column this formation holds at once it has flown in.
            final int home = randRange(rng, HOLD_MIN_X, HOLD_MAX_X);
            List<SpawnDetails> out = new ArrayList<>();
            switch (this) {
                case SINGLE:
                    out.add(alien(frame, randRange(rng, minY, maxY), home));
                    break;
                case COLUMN: {
                    int top = randRange(rng, minY, maxY - 3 * SPACING);
                    for (int i = 0; i < 4; i++) {
                        out.add(alien(frame, top + i * SPACING, home));
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
                        out.add(alien(frame + i * 6, startY + i * step, home + i * 8));
                    }
                    break;
                }
                case VEE: {
                    int cy = randRange(rng, minY + 2 * SPACING, maxY - 2 * SPACING);
                    out.add(alien(frame, cy, home - 40));          // tip forward
                    out.add(alien(frame + 4, cy - SPACING, home));
                    out.add(alien(frame + 4, cy + SPACING, home));
                    out.add(alien(frame + 8, cy - 2 * SPACING, home + 20));
                    out.add(alien(frame + 8, cy + 2 * SPACING, home + 20));
                    break;
                }
                case POWERUP:
                    out.add(new SpawnDetails(frame, "PowerUp-SpeedUp",
                            BOARD_WIDTH, randRange(rng, minY, maxY)));
                    break;
            }
            return out;
        }
    }

    private static SpawnDetails alien(int frame, int y, int homeX) {
        return new SpawnDetails(frame, "Alien1", BOARD_WIDTH, y, homeX);
    }

    private static int randRange(Random rng, int lo, int hi) {
        if (hi <= lo) {
            return lo;
        }
        return lo + rng.nextInt(hi - lo + 1);
    }
}
