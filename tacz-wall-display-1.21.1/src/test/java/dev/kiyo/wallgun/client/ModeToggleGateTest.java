package dev.kiyo.wallgun.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModeToggleGateTest {
    @Test void heldRepeatOnlySwitchesOnceAndReleaseRearms() {
        var gate = new ModeToggleGate();
        assertTrue(gate.accept(true, true, true));
        assertFalse(gate.accept(true, true, true));
        assertFalse(gate.accept(true, false, true));
        assertTrue(gate.accept(true, true, true));
    }
    @Test void shortClickStillWorks() {
        assertTrue(new ModeToggleGate().accept(true, false, true));
    }
    @Test void uiInputCannotActivateWhenReturningHeld() {
        var gate = new ModeToggleGate();
        assertFalse(gate.accept(true, true, false));
        assertFalse(gate.accept(true, true, true));
        assertFalse(gate.accept(false, false, true));
        assertTrue(gate.accept(true, true, true));
    }
}
