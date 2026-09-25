package dev.kiyo.wallgun.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderDistanceRulesTest {
    @Test void finiteDistanceAndUnlimitedSentinel() {
        Vec3 camera = Vec3.ZERO;
        AABB far = new AABB(513, 0, 0, 514, 1, 1);
        assertFalse(RenderDistanceRules.draw(far, camera, 512));
        assertTrue(RenderDistanceRules.draw(far, camera, -1));
        assertTrue(RenderDistanceRules.draw(new AABB(512, 0, 0, 513, 1, 1), camera, 512));
        assertFalse(RenderDistanceRules.keep(new BlockPos(528, 0, 0), camera, 512));
        assertTrue(RenderDistanceRules.keep(new BlockPos(528, 0, 0), camera, -1));
    }

    @Test void discoveryStopsAtTheLoadedChunkHorizon() {
        assertEquals(20, RenderDistanceRules.scanRadius(512, 20));
        assertEquals(33, RenderDistanceRules.scanRadius(512, 40));
        assertEquals(40, RenderDistanceRules.scanRadius(-1, 40));
    }
}
