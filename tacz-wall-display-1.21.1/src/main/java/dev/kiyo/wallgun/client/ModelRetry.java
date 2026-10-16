package dev.kiyo.wallgun.client;

/** Initial attempt plus two delayed retries. Reset only on resource reload or cache retirement. */
public final class ModelRetry {
    private int failures;
    private long next;
    public void failed(long now) {failures++;next=now+(failures==1?2_000_000_000L:10_000_000_000L);}
    public boolean due(long now) {return failures<3 && now>=next;}
}
