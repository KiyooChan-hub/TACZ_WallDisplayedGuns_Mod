package dev.kiyo.wallgun;
import java.util.List;

/** Resolved prefix, unresolved suffix. Each tick visits the suffix at most once. */
public final class ChunkScan {
    private ChunkScan() {}
    public static <T> void retryLater(List<T> positions,int cursor) {
        positions.add(positions.remove(cursor));
    }
    public static boolean complete(List<?> positions,int cursor) {return cursor==positions.size();}
}
