package dev.kiyo.wallgun;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkScanTest {
    @Test void unknownIsNotAnEmptyCompletedScene() {
        var chunks=new ArrayList<>(List.of("unknown"));int cursor=0;
        ChunkScan.retryLater(chunks,cursor);
        assertFalse(ChunkScan.complete(chunks,cursor));
        cursor++; // only after the chunk becomes readable and is inspected
        assertTrue(ChunkScan.complete(chunks,cursor));
    }
    @Test void delayedGunChunkIsStillExaminedAfterReadyEmptyChunks() {
        var chunks=new ArrayList<>(List.of("delayed-gun","empty","ready-gun"));int cursor=0;
        ChunkScan.retryLater(chunks,cursor);
        assertEquals("empty",chunks.get(cursor++));
        assertEquals("ready-gun",chunks.get(cursor++));
        assertFalse(ChunkScan.complete(chunks,cursor));
        assertEquals("delayed-gun",chunks.get(cursor++));
        assertTrue(ChunkScan.complete(chunks,cursor));
    }
    @Test void repeatedUnknownsNeitherGrowQueueNorStarveReadyOnes() {
        var chunks=new ArrayList<>(List.of(1,2,3));int cursor=0;
        for(int tick=0;tick<5;tick++) {
            int remaining=chunks.size()-cursor;
            while(remaining-->0)ChunkScan.retryLater(chunks,cursor);
            assertEquals(List.of(1,2,3),chunks);
            assertFalse(ChunkScan.complete(chunks,cursor));
        }
    }
}
