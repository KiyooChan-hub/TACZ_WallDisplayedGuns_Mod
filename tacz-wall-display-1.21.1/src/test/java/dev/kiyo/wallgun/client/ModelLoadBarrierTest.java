package dev.kiyo.wallgun.client;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ModelLoadBarrierTest {
    @Test void joinsRemovedSupplierBeforeModelPublication() throws Exception {
        Object manager = new Object();
        AtomicReference<String> model = new AtomicReference<>();
        CountDownLatch removed = new CountDownLatch(1), publish = new CountDownLatch(1), lookupStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> parsing = executor.submit(() -> {
                synchronized(manager) {
                    removed.countDown();
                    try { assertTrue(publish.await(5, TimeUnit.SECONDS)); }
                    catch(InterruptedException e) { throw new RuntimeException(e); }
                    model.set("parsed model");
                }
            });
            assertTrue(removed.await(5, TimeUnit.SECONDS));
            assertNull(model.get(), "TACZ's unlocked lookup observes the transient null");
            Future<String> capture = executor.submit(() -> { lookupStarted.countDown(); return ModelLoadBarrier.load(manager, model::get); });
            assertTrue(lookupStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> capture.get(100, TimeUnit.MILLISECONDS));
            publish.countDown();
            assertEquals("parsed model", capture.get(5, TimeUnit.SECONDS));
            parsing.get(5, TimeUnit.SECONDS);
        } finally { publish.countDown(); executor.shutdownNow(); }
    }
    @Test void genuineMissingResourceRemainsMissing() {
        assertNull(ModelLoadBarrier.load(new Object(), () -> null));
    }
}
