package dev.kiyo.wallgun;

/** A coalesced initialization request, never a queue of combat actions. */
public final class DeferredGunDraw {
    private boolean pending;
    public void request(){pending=true;}
    public boolean pending(){return pending;}
    public boolean finish(boolean missingInitialization,Runnable initialize) {
        if(!pending && !missingInitialization)return false;
        initialize.run();
        pending=false;
        return true;
    }
}
