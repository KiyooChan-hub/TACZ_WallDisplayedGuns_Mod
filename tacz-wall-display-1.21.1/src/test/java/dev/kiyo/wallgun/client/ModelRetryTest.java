package dev.kiyo.wallgun.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModelRetryTest {
    @Test void retryIsDelayedAndExhaustedAfterThreeFailures() {
        var retry=new ModelRetry();retry.failed(100);
        assertFalse(retry.due(2_000_000_099L));assertTrue(retry.due(2_000_000_100L));
        retry.failed(3_000_000_000L);
        assertFalse(retry.due(12_999_999_999L));assertTrue(retry.due(13_000_000_000L));
        retry.failed(14_000_000_000L);assertFalse(retry.due(Long.MAX_VALUE));
    }
}
