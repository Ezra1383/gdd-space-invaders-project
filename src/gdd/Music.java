package gdd;

import java.io.File;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * Background-music player that switches tracks on demand — one looping clip at a
 * time. {@link #play} swaps to a new track, and if the requested file is missing
 * or unplayable it keeps whatever is already playing, so gameplay never breaks
 * over an absent song.
 *
 * Note: Java's {@link Clip} plays WAV/AIFF (PCM) only — not MP3 — so every track
 * must be a {@code .wav}. Missing files are a silent no-op by design, which lets
 * the biome/boss tracks be dropped in one at a time.
 */
public final class Music {

    private Clip clip;
    private String current; // path of the track currently playing, or null
    private boolean muted;
    private boolean paused;

    /**
     * Switches to {@code path}, looping. Returns true if that track is now the
     * one selected (including when it already was); false if it couldn't be
     * loaded, in which case the current track keeps playing untouched. Respects
     * the current mute/pause state, so switching tracks while muted stays silent.
     */
    public boolean play(String path) {
        if (path == null) {
            return false;
        }
        if (path.equals(current)) {
            return true; // already selected
        }
        Clip next;
        try {
            AudioInputStream in =
                    AudioSystem.getAudioInputStream(new File(path).getAbsoluteFile());
            next = AudioSystem.getClip();
            next.open(in);
        } catch (Exception e) {
            // Missing or unsupported (e.g. MP3): leave the current track playing.
            return false;
        }
        stop();
        clip = next;
        current = path;
        clip.setFramePosition(0);
        clip.loop(Clip.LOOP_CONTINUOUSLY); // this also starts playback
        applyState();
        return true;
    }

    /** Silences/unsilences the music; the track keeps its place underneath. */
    public void setMuted(boolean m) {
        muted = m;
        applyState();
    }

    public boolean isMuted() {
        return muted;
    }

    /** Freezes/resumes playback for a game pause, independent of mute. */
    public void setPaused(boolean p) {
        paused = p;
        applyState();
    }

    /** Runs the clip only when neither muted nor paused. */
    private void applyState() {
        if (clip == null) {
            return;
        }
        if (muted || paused) {
            clip.stop();
        } else if (!clip.isRunning()) {
            clip.start(); // resumes the loop from where it stopped
        }
    }

    /** Stops and releases the current track. */
    public void stop() {
        if (clip != null) {
            try {
                clip.stop();
                clip.close();
            } catch (Exception ignored) {
                // never let audio break gameplay
            }
            clip = null;
        }
        current = null;
    }
}
