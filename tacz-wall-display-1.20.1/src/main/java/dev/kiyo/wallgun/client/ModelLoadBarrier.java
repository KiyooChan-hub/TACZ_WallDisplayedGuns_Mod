package dev.kiyo.wallgun.client;
import java.util.function.Supplier;
/** Join an in-flight TACZ lazy load before its unlocked fast path can return null. */
final class ModelLoadBarrier {
    static <T> T load(Object manager, Supplier<T> lookup) {
        synchronized (manager) { return lookup.get(); }
    }
}
