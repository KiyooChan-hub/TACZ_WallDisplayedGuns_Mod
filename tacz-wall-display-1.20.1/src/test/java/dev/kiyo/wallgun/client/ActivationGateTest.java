package dev.kiyo.wallgun.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivationGateTest {
    @Test void normalLoadingRetiresBeforeControlsResume() {
        var gate=new ActivationGate();gate.entering();
        assertTrue(gate.waiting());
        assertEquals(ActivationGate.Offer.PREPARE,gate.offer(10));
        assertTrue(gate.finish(10));assertFalse(gate.waiting());
        assertEquals(ActivationGate.Offer.STALE,gate.offer(10));
        assertFalse(gate.waiting());
    }
    @Test void lateOfferDuringGameplayNeverFreezesPlayer() {
        var gate=new ActivationGate();
        assertEquals(ActivationGate.Offer.RESUME,gate.offer(25));
        assertFalse(gate.waiting());assertTrue(gate.finish(25));
        assertEquals(ActivationGate.Offer.STALE,gate.offer(25));
    }
    @Test void oldReleaseCannotEndNewDimensionLoading() {
        var gate=new ActivationGate();gate.entering();gate.offer(10);
        gate.entering();assertFalse(gate.finish(10));
        assertEquals(ActivationGate.Offer.STALE,gate.offer(10));
        assertEquals(ActivationGate.Offer.PREPARE,gate.offer(11));
        assertFalse(gate.finish(10));assertTrue(gate.waiting());
        assertTrue(gate.finish(11));
    }
    @Test void duplicateAndReorderedOffersCannotReplaceNewerSession() {
        var gate=new ActivationGate();gate.entering();gate.offer(10);
        assertEquals(ActivationGate.Offer.PREPARE,gate.offer(11));
        assertEquals(ActivationGate.Offer.STALE,gate.offer(10));
        assertEquals(ActivationGate.Offer.PREPARE,gate.offer(11));
        assertEquals(11,gate.current());assertTrue(gate.waiting());
    }
    @Test void staleStatusCannotReleaseNextLoadingGeneration() {
        var gate=new ActivationGate();gate.entering();long old=gate.generation();
        gate.entering();assertFalse(gate.confirmActive(old));assertTrue(gate.waiting());
        assertTrue(gate.confirmActive(gate.generation()));assertFalse(gate.waiting());
    }
    @Test void noSessionReplyCannotOverrideAnAcceptedOffer() {
        var gate=new ActivationGate();gate.entering();long generation=gate.generation();
        gate.offer(15);assertFalse(gate.confirmActive(generation));assertTrue(gate.waiting());
        gate.finish(15);assertFalse(gate.confirmActive(generation));
    }
    @Test void reconnectToAnotherServerResetsOrdering() {
        var gate=new ActivationGate();gate.entering();gate.offer(100);gate.finish(100);
        gate.disconnect();gate.entering();
        assertEquals(ActivationGate.Offer.PREPARE,gate.offer(1));assertTrue(gate.finish(1));
    }
    @Test void noOfferAtSixtySecondsStillRequiresServerConfirmation() {
        var gate=new ActivationGate();gate.entering();
        // Elapsed wall time does not grant controls; only this generation's reply does.
        assertFalse(gate.finish(0));assertTrue(gate.waiting());
        assertTrue(gate.confirmActive(gate.generation()));assertFalse(gate.waiting());
    }
}
