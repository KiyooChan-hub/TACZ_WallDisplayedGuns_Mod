package dev.kiyo.wallgun.client;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class WorkBudgetTest {
    @Test void oversizedCaptureCannotStartAnotherJobInSameFrame() {
        var clock=new AtomicLong();var budget=new WorkBudget(2_000_000,clock::get);
        assertTrue(budget.start());clock.addAndGet(68_000_000);
        assertFalse(budget.start());assertFalse(budget.start());
        assertTrue(new WorkBudget(2_000_000,clock::get).start());
    }
    @Test void expiredFrameStillMakesOneJobOfProgress() {
        var clock=new AtomicLong();var budget=new WorkBudget(2_000_000,clock::get);
        clock.addAndGet(10_000_000);
        assertTrue(budget.start());assertFalse(budget.start());
    }
}
