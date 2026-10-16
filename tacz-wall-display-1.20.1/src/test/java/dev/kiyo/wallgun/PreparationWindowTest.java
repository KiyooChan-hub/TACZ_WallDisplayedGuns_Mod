package dev.kiyo.wallgun;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparationWindowTest {
    private static final long SECOND=1_000_000_000L;
    @Test void slowHandshakeThenDelayedGunChunkCompletesBeforeBudget() {
        var window=new PreparationWindow();window.enter(0,10*SECOND);
        var chunks=new java.util.ArrayList<>(java.util.List.of("empty","gun"));
        assertFalse(window.preparationExpired(18*SECOND));
        window.begin(18*SECOND);
        int cursor=1; // first readable chunk was empty
        ChunkScan.retryLater(chunks,cursor); // gun chunk is still arriving
        assertFalse(ChunkScan.complete(chunks,cursor));
        assertFalse(window.preparationExpired(20*SECOND));
        assertEquals("gun",chunks.get(cursor++)); // discovered once ready, not lost
        assertTrue(ChunkScan.complete(chunks,cursor));
        assertFalse(window.preparationExpired(24*SECOND)); // capture and upload completed
        assertFalse(window.activationExpired(24*SECOND));
    }
    @Test void slowHandshakeDoesNotSpendPreparationBudget() {
        var window=new PreparationWindow();window.enter(0,10*SECOND);
        assertFalse(window.preparationExpired(18*SECOND));
        assertFalse(window.activationExpired(18*SECOND));
        assertTrue(window.begin(18*SECOND));
        assertFalse(window.preparationExpired(27*SECOND));
        assertTrue(window.preparationExpired(28*SECOND));
    }
    @Test void duplicateOffersAndRevisionsCannotExtendPreparation() {
        var window=new PreparationWindow();window.enter(0,10*SECOND);
        assertTrue(window.begin(15*SECOND));assertFalse(window.begin(24*SECOND));
        assertEquals(9*SECOND,window.preparationElapsed(24*SECOND));
        assertTrue(window.preparationExpired(25*SECOND));
    }
    @Test void overallDeadlineAlsoBoundsLateHandshake() {
        var window=new PreparationWindow();window.enter(0,10*SECOND);window.begin(55*SECOND);
        assertFalse(window.preparationExpired(60*SECOND));assertTrue(window.activationExpired(60*SECOND));
    }
    @Test void newWorldStartsAnIndependentWindow() {
        var window=new PreparationWindow();window.enter(0,10*SECOND);window.begin(SECOND);
        window.enter(100*SECOND,10*SECOND);
        assertFalse(window.preparationExpired(118*SECOND));assertEquals(0,window.preparationElapsed(118*SECOND));
        assertTrue(window.begin(118*SECOND));assertFalse(window.preparationExpired(119*SECOND));
    }
}
