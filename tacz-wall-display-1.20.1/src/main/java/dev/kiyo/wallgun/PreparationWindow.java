package dev.kiyo.wallgun;

/** Two monotonic, non-renewable deadlines; no Minecraft dependency for deterministic timing tests. */
public final class PreparationWindow {
    private long entered, preparation, budget;
    private boolean begun;
    public void enter(long now,long budgetNanos) {entered=now;budget=budgetNanos;begun=false;}
    public boolean begin(long now) {
        if(begun)return false;
        begun=true;preparation=now;return true;
    }
    public boolean activationExpired(long now) {return now-entered>=60_000_000_000L;}
    public boolean preparationExpired(long now) {return begun && now-preparation>=budget;}
    public long preparationElapsed(long now) {return begun?Math.max(0,now-preparation):0;}
}
