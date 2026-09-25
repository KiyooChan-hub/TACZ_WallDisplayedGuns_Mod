package dev.kiyo.wallgun.client;

import java.util.ArrayDeque;
import java.util.List;

/** Time-based HUD animation; a retrigger samples the exact current scale. */
public final class DryFireWarning {
    private static final long GROW = 160_000_000L;
    private static final long RETURN = 180_000_000L;
    private static final long HOLD = 2_000_000_000L;
    private static final long FADE = 500_000_000L;
    private static final long SOUND_WINDOW = 3_000_000_000L;
    private static final long DURATION = GROW + RETURN + HOLD + FADE;
    private static final double MAX_SCALE = 1.3;
    private final ArrayDeque<Long> recentTriggers = new ArrayDeque<>();
    private Pulse current;
    private boolean sounding;
    private long soundEndsAt;

    public record Frame(double scale, double opacity) {}

    /** Returns whether this click should play the empty-gun sound. */
    public boolean trigger(long now) {
        if (sounding && now >= soundEndsAt) resetSoundCount();
        if (!sounding) {
            while (!recentTriggers.isEmpty() && now - recentTriggers.peekFirst() > SOUND_WINDOW)
                recentTriggers.removeFirst();
            recentTriggers.addLast(now);
            if (recentTriggers.size() >= 3) sounding = true;
        }
        if (current != null && current.age(now) < GROW + RETURN + HOLD) {
            double start = current.frame(now).scale();
            current = new Pulse(now, start, Math.min(MAX_SCALE, start + 0.2));
        } else {
            // A retrigger during fade replaces the old text immediately.
            current = new Pulse(now, 1.0, 1.2);
        }
        if (sounding) soundEndsAt = now + DURATION;
        return sounding;
    }

    public List<Frame> frames(long now) {
        if (sounding && now >= soundEndsAt) resetSoundCount();
        if (current == null) return List.of();
        if (current.expired(now)) { current = null; return List.of(); }
        return List.of(current.frame(now));
    }

    public void clear() { current = null; resetSoundCount(); }

    private void resetSoundCount() {
        recentTriggers.clear();
        sounding = false;
        soundEndsAt = 0;
    }

    private record Pulse(long start, double initial, double peak) {
        private long age(long now) { return Math.max(0, now - start); }
        private boolean expired(long now) { return age(now) >= DURATION; }
        private Frame frame(long now) {
            long age = age(now);
            if (age < GROW) return new Frame(initial + (peak - initial) * ease(age / (double) GROW), 1);
            if (age < GROW + RETURN) return new Frame(peak + (1 - peak) * ease((age - GROW) / (double) RETURN), 1);
            if (age < GROW + RETURN + HOLD) return new Frame(1, 1);
            return new Frame(1, Math.max(0, 1 - (age - GROW - RETURN - HOLD) / (double) FADE));
        }
    }

    private static double ease(double fraction) {
        return fraction * fraction * (3 - 2 * fraction);
    }
}
