package dev.kiyo.wallgun.client;

import java.util.function.LongSupplier;

/** Stops starting new indivisible jobs after the budget; it cannot interrupt a TACZ capture. */
public final class WorkBudget {
    private final LongSupplier clock;
    private final long deadline;
    private int jobs;
    public WorkBudget(long nanos) { this(nanos, System::nanoTime); }
    WorkBudget(long nanos, LongSupplier clock) { this.clock=clock; deadline=clock.getAsLong()+nanos; }
    public boolean start() {
        if (jobs!=0 && clock.getAsLong()>=deadline) return false;
        jobs++; return true;
    }
}
