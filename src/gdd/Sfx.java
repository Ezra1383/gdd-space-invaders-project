package gdd;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * Retro sound effects, synthesised in code (Stage 9) — no audio files required.
 *
 * Each effect is rendered once into a PCM buffer at class-load and held in a
 * small pool of pre-opened {@link Clip}s so repeats can overlap without the
 * latency of opening a line per shot. If the machine has no usable audio, every
 * voice degrades to a silent no-op rather than breaking the game.
 *
 * To swap in real audio later, replace a voice's buffer with a loaded WAV.
 */
public final class Sfx {

    private static final float RATE = 44100f;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    private static final Voice SHOOT = voice(synthShoot(), 4);
    private static final Voice ENEMY_EXPLODE = voice(synthEnemyExplode(), 4);
    private static final Voice PLAYER_DEATH = voice(synthPlayerDeath(), 1);
    private static final Voice POWERUP = voice(synthPowerup(), 2);
    private static final Voice BOSS_WARN = voice(synthBossWarn(), 1);
    private static final Voice BOSS_DEATH = voice(synthBossDeath(), 1);

    private Sfx() {
    }

    /**
     * Forces class-load (synthesis + clip-pool opening, ~70ms) up front so the
     * first shot in-game doesn't hitch.
     */
    public static void init() {
        // Touching the class is enough; static initialisers have already run.
    }

    public static void shoot() {
        SHOOT.play();
    }

    public static void enemyExplode() {
        ENEMY_EXPLODE.play();
    }

    public static void playerDeath() {
        PLAYER_DEATH.play();
    }

    public static void powerup() {
        POWERUP.play();
    }

    public static void bossWarn() {
        BOSS_WARN.play();
    }

    public static void bossDeath() {
        BOSS_DEATH.play();
    }

    // --- voices -----------------------------------------------------------

    private static Voice voice(double[] samples, int poolSize) {
        try {
            return new Voice(toBytes(samples), poolSize);
        } catch (Exception e) {
            return new Voice(); // silent fallback
        }
    }

    /** A pool of pre-opened clips for one sound, rotated per play. */
    private static final class Voice {
        private final Clip[] pool;
        private int idx = 0;

        Voice() {
            this.pool = null; // silent
        }

        Voice(byte[] data, int poolSize) throws Exception {
            Clip[] clips = new Clip[poolSize];
            for (int i = 0; i < poolSize; i++) {
                Clip c = AudioSystem.getClip();
                c.open(FORMAT, data, 0, data.length);
                clips[i] = c;
            }
            this.pool = clips;
        }

        void play() {
            if (pool == null) {
                return;
            }
            try {
                Clip c = pool[idx];
                idx = (idx + 1) % pool.length;
                c.stop();
                c.setFramePosition(0);
                c.start();
            } catch (Exception ignored) {
                // never let audio break gameplay
            }
        }
    }

    // --- synthesis --------------------------------------------------------

    /** Short descending square "pew". */
    private static double[] synthShoot() {
        return sweep(950, 320, 0.07, 0.18, true, 5);
    }

    /** Filtered noise burst. */
    private static double[] synthEnemyExplode() {
        return noise(0.22, 0.28, 6);
    }

    /** Descending tone under a long noise burst. */
    private static double[] synthPlayerDeath() {
        return mix(sweep(420, 60, 0.7, 0.3, true, 2.5), noise(0.7, 0.28, 2.5));
    }

    /** Rising blip. */
    private static double[] synthPowerup() {
        return concat(sweep(620, 900, 0.07, 0.22, false, 1.5),
                sweep(950, 1350, 0.09, 0.22, false, 2));
    }

    /** Two-tone alarm. */
    private static double[] synthBossWarn() {
        return concat(
                sweep(500, 500, 0.16, 0.22, true, 0.5),
                sweep(360, 360, 0.16, 0.22, true, 0.5),
                sweep(500, 500, 0.16, 0.22, true, 0.5),
                sweep(360, 360, 0.16, 0.22, true, 0.5));
    }

    /** Big, long boom. */
    private static double[] synthBossDeath() {
        return mix(noise(0.9, 0.38, 2.2), sweep(300, 40, 0.9, 0.26, true, 2));
    }

    /**
     * Frequency sweep from f0 to f1 with exponential decay.
     *
     * @param square true for a square wave (harsher, retro), false for sine
     * @param decay  higher = faster fade
     */
    private static double[] sweep(double f0, double f1, double secs, double vol,
                                  boolean square, double decay) {
        int n = (int) (RATE * secs);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double f = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * f / RATE;
            double s = square ? (Math.sin(phase) >= 0 ? 1 : -1) : Math.sin(phase);
            out[i] = s * Math.exp(-decay * t) * vol;
        }
        return out;
    }

    /** Low-passed white noise with exponential decay. */
    private static double[] noise(double secs, double vol, double decay) {
        int n = (int) (RATE * secs);
        double[] out = new double[n];
        java.util.Random r = new java.util.Random(1234);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double s = r.nextDouble() * 2 - 1;
            lp = lp * 0.65 + s * 0.35; // simple low-pass for body
            out[i] = lp * Math.exp(-decay * t) * vol;
        }
        return out;
    }

    private static double[] mix(double[] a, double[] b) {
        double[] out = new double[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++) {
            double v = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
            out[i] = Math.max(-1, Math.min(1, v));
        }
        return out;
    }

    private static double[] concat(double[]... parts) {
        int n = 0;
        for (double[] p : parts) {
            n += p.length;
        }
        double[] out = new double[n];
        int o = 0;
        for (double[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    private static byte[] toBytes(double[] buf) {
        byte[] out = new byte[buf.length * 2];
        for (int i = 0; i < buf.length; i++) {
            int v = (int) (Math.max(-1, Math.min(1, buf[i])) * 32767);
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }
}
