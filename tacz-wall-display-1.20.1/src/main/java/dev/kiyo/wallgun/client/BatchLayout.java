package dev.kiyo.wallgun.client;

import net.minecraft.core.BlockPos;

/** Fixed spatial cells: edits never move unrelated instances between batches. */
public final class BatchLayout {
    public static final int MAX_GUNS = 8;
    private BatchLayout() {}

    public static int cell(BlockPos pos) {
        return ((pos.getX() & 15) >> 1) | (((pos.getY() & 15) >> 1) << 3) | (((pos.getZ() & 15) >> 1) << 6);
    }
}
