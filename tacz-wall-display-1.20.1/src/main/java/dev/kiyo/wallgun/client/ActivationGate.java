package dev.kiyo.wallgun.client;

/** Connection-local ordering. Only a real level-loading event may enter the waiting state. */
public final class ActivationGate {
    public enum Offer { PREPARE, RESUME, STALE }
    private long current, retired, generation;
    private boolean waiting;

    public void entering() {
        generation++;
        retired = Math.max(retired, current);
        current = 0;
        waiting = true;
    }
    public Offer offer(long id) {
        if (id <= 0 || id <= retired || id < current) return Offer.STALE;
        if (id != current) {
            retired = Math.max(retired, current);
            current = id;
        }
        return waiting ? Offer.PREPARE : Offer.RESUME;
    }
    public boolean finish(long id) {
        if (id == 0 || id != current) return false;
        retired = Math.max(retired, current);
        current = 0;
        waiting = false;
        return true;
    }
    public long current() { return current; }
    public long generation() { return generation; }
    public long retired() { return retired; }
    public boolean confirmActive(long replyGeneration) {
        if (!waiting || current != 0 || replyGeneration != generation) return false;
        waiting = false;
        return true;
    }
    public boolean waiting() { return waiting; }
    public void disconnect() { current = retired = generation = 0; waiting = false; }
}
