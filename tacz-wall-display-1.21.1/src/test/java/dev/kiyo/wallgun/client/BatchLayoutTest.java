package dev.kiyo.wallgun.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchLayoutTest {
    @Test void denseSectionsNeverExceedEightInstancesPerCellIncludingNegativeCoordinates() {
        for(int origin:new int[]{-32,-16,0,16}) {
            int[] counts=new int[512];
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)
                counts[BatchLayout.cell(new BlockPos(origin+x,origin+y,origin+z))]++;
            for(int count:counts)assertEquals(BatchLayout.MAX_GUNS,count);
        }
    }
    @Test void adjacentCellsKeepIndependentStableMembership() {
        assertEquals(BatchLayout.cell(new BlockPos(4,6,8)),BatchLayout.cell(new BlockPos(5,7,9)));
        assertNotEquals(BatchLayout.cell(new BlockPos(4,6,8)),BatchLayout.cell(new BlockPos(6,6,8)));
    }
}
