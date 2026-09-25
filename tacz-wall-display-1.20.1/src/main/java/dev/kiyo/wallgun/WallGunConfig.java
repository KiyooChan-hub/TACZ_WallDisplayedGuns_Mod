package dev.kiyo.wallgun;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only render preference. -1 removes the addon's distance limit. */
public final class WallGunConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<Integer> MAX_RENDER_DISTANCE;
    static {
        var builder = new ForgeConfigSpec.Builder();
        MAX_RENDER_DISTANCE = builder.comment("Maximum decorative-gun render distance in blocks (1-512); -1 disables the addon's limit.")
                .define("maxRenderDistance", 512, value -> value instanceof Integer distance
                        && (distance == -1 || distance >= 1 && distance <= 512));
        SPEC = builder.build();
    }
    private WallGunConfig() {}
    public static int maxRenderDistance() { return MAX_RENDER_DISTANCE.get(); }
}
