package dev.kiyo.wallgun;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DeferredGunDrawTest {
    @Test void repeatedRequestsInitializeOnlyOnce() {
        var draw=new DeferredGunDraw();var calls=new AtomicInteger();
        draw.request();draw.request();draw.request();
        assertTrue(draw.finish(false,calls::incrementAndGet));
        assertFalse(draw.finish(false,calls::incrementAndGet));
        assertEquals(1,calls.get());assertFalse(draw.pending());
    }
    @Test void releaseRepairsMissingInitialStateWithoutARecordedPacket() {
        var draw=new DeferredGunDraw();var calls=new AtomicInteger();
        assertTrue(draw.finish(true,calls::incrementAndGet));
        assertEquals(1,calls.get());
    }
    @Test void initializedGunWithoutBlockedDrawIsNotReset() {
        var draw=new DeferredGunDraw();
        assertFalse(draw.finish(false,()->fail("must not reset an initialized gun")));
    }
    @Test void failedInitializationIsNotAcknowledgedAsCompleted() {
        var draw=new DeferredGunDraw();draw.request();
        assertThrows(IllegalStateException.class,()->draw.finish(false,()->{throw new IllegalStateException();}));
        assertTrue(draw.pending());
    }
    @Test void delayedRequestUsesStateAtReleaseNotStateAtArrival() {
        var draw=new DeferredGunDraw();var selected=new AtomicInteger(1);var bound=new AtomicInteger(-1);
        draw.request();selected.set(5);
        draw.finish(false,()->bound.set(selected.get()));
        assertEquals(5,bound.get());
    }
}
