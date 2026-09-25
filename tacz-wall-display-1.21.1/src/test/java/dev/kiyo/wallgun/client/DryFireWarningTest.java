package dev.kiyo.wallgun.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DryFireWarningTest {
    @Test void retriggerContinuesFromCurrentScaleAndNeverExceedsCap() {
        var warning = new DryFireWarning();
        warning.trigger(0);
        assertEquals(1.1, warning.frames(80_000_000L).getFirst().scale(), 0.0001);
        warning.trigger(80_000_000L);
        assertEquals(1.1, warning.frames(80_000_000L).getFirst().scale(), 0.0001);
        assertEquals(1.3, warning.frames(240_000_000L).getFirst().scale(), 0.0001);
        warning.trigger(240_000_000L);
        assertEquals(1.3, warning.frames(400_000_000L).getFirst().scale(), 0.0001);
        assertEquals(1.0, warning.frames(580_000_000L).getFirst().scale(), 0.0001);
    }

    @Test void retriggerDuringFadeLeavesOldPulseAndStartsFreshOne() {
        var warning = new DryFireWarning();
        warning.trigger(0);
        long fading = 2_400_000_000L;
        assertTrue(warning.frames(fading).getFirst().opacity() < 1);
        warning.trigger(fading);
        var frames = warning.frames(fading);
        assertEquals(2, frames.size());
        assertTrue(frames.getFirst().opacity() < 1);
        assertEquals(1, frames.get(1).opacity(), 0.0001);
        assertEquals(1.2, warning.frames(fading + 160_000_000L).getLast().scale(), 0.0001);
        assertEquals(1, warning.frames(fading + 500_000_000L).size());
    }
}
