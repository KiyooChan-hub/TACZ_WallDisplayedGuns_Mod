package dev.kiyo.wallgun.client;

/** One transition per physical press; discarded UI input cannot leak into gameplay. */
final class ModeToggleGate {
    private boolean held;
    boolean accept(boolean clicked, boolean down, boolean available) {
        boolean accept = available && clicked && !held;
        held = down;
        return accept;
    }
    void reset() { held = false; }
}
