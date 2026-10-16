package dev.kiyo.wallgun;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreloadScopeTest {
    @Test void radiusTwoContainsExactlyTwentyFiveChunks() {
        int count=0;for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)
            if(PreloadScope.contains(0,0,x,z,2))count++;
        assertEquals(25,count);
    }
    @Test void cornersNegativeCoordinatesAndZeroRadius() {
        assertTrue(PreloadScope.contains(-10,-20,-12,-18,2));
        assertFalse(PreloadScope.contains(-10,-20,-13,-18,2));
        assertTrue(PreloadScope.contains(-1,-1,-1,-1,0));
        assertFalse(PreloadScope.contains(-1,-1,0,-1,0));
        assertFalse(PreloadScope.contains(0,0,0,0,-1));
        assertFalse(PreloadScope.contains(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0,2));
    }
}
