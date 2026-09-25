package dev.kiyo.wallgun.client;

import java.util.ArrayList;
import java.util.List;

/** Time-based HUD animation; a retrigger samples the exact current scale. */
public final class DryFireWarning {
    private static final long GROW = 160_000_000L;
    private static final long RETURN = 180_000_000L;
    private static final long HOLD = 2_000_000_000L;
    private static final long FADE = 500_000_000L;
    private static final double MAX_SCALE = 1.3;
    private final List<Pulse> fading = new ArrayList<>();
    private Pulse current;

    public record Frame(double scale, double opacity) {}

    public void trigger(long now) {
        if (current != null && current.age(now) < GROW + RETURN + HOLD) {
            double start = current.frame(now).scale();
            current = new Pulse(now, start, Math.min(MAX_SCALE, start + 0.2));
        } else {
            if (current != null && current.age(now) < GROW + RETURN + HOLD + FADE) fading.add(current);
            current = new Pulse(now, 1.0, 1.2);
        }
        fading.removeIf(pulse -> pulse.expired(now));
    }

    public List<Frame> frames(long now) {
        fading.removeIf(pulse -> pulse.expired(now));
        var result = new ArrayList<Frame>(fading.size() + 1);
        for (Pulse pulse : fading) result.add(pulse.frame(now));
        if (current != null) {
            if (current.expired(now)) current = null;
            else result.add(current.frame(now));
        }
        return result;
    }

    public void clear() { current = null; fading.clear(); }

    private record Pulse(long start, double initial, double peak) {
        private long age(long now) { return Math.max(0, now - start); }
        private boolean expired(long now) { return age(now) >= GROW + RETURN + HOLD + FADE; }
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
