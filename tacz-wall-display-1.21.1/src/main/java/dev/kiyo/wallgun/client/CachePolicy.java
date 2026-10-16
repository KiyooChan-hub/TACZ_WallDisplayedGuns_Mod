package dev.kiyo.wallgun.client;

/** Decides retention only; active-scene rendering still needs reusable geometry. */
public final class CachePolicy {
    private boolean previouslyEnabled=true;
    public boolean release(boolean enabled,boolean worldChanged){
        boolean release=!enabled && (previouslyEnabled || worldChanged);
        previouslyEnabled=enabled;
        return release;
    }
}
