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

    @Test void retriggerDuringFadeReplacesOldPulse() {
        var warning = new DryFireWarning();
        warning.trigger(0);
        long fading = 2_400_000_000L;
        assertTrue(warning.frames(fading).getFirst().opacity() < 1);
        warning.trigger(fading);
        var frames = warning.frames(fading);
        assertEquals(1, frames.size());
        assertEquals(1, frames.getFirst().opacity(), 0.0001);
        assertEquals(1.2, warning.frames(fading + 160_000_000L).getFirst().scale(), 0.0001);
        assertEquals(1, warning.frames(fading + 500_000_000L).size());
    }

    @Test void thirdClickWithinThreeSecondsArmsSoundUntilLastWarningExpires() {
        var warning = new DryFireWarning();
        assertFalse(warning.trigger(0));
        assertFalse(warning.trigger(1_000_000_000L));
        assertTrue(warning.trigger(2_000_000_000L));
        assertTrue(warning.trigger(3_500_000_000L));
        assertTrue(warning.frames(6_339_999_999L).getFirst().opacity() > 0);
        assertFalse(warning.trigger(6_340_000_000L));
        assertFalse(warning.trigger(6_500_000_000L));
        assertTrue(warning.trigger(6_600_000_000L));
    }

    @Test void oldClicksOutsideThreeSecondWindowDoNotArmSound() {
        var warning = new DryFireWarning();
        assertFalse(warning.trigger(0));
        assertFalse(warning.trigger(3_000_000_001L));
        assertFalse(warning.trigger(6_000_000_002L));
    }
}
