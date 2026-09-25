package dev.kiyo.wallgun.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One distance convention for discovery, residency and actual drawing. */
public final class RenderDistanceRules {
    private static final int RESIDENCY_MARGIN = 16;
    private RenderDistanceRules() {}

    public static boolean draw(AABB bounds, Vec3 camera, int maxDistance) {
        return maxDistance == -1 || bounds.distanceToSqr(camera) <= (double) maxDistance * maxDistance;
    }

    public static boolean keep(BlockPos pos, Vec3 camera, int maxDistance) {
        int keepDistance = maxDistance + RESIDENCY_MARGIN;
        return maxDistance == -1 || pos.distToCenterSqr(camera) <= (double) keepDistance * keepDistance;
    }

    public static int scanRadius(int maxDistance, int effectiveRenderDistance) {
        if (maxDistance == -1) return effectiveRenderDistance;
        return Math.min(effectiveRenderDistance, (maxDistance + RESIDENCY_MARGIN + 15) / 16);
    }
}
