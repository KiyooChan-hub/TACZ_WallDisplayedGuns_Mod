package dev.kiyo.wallgun;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only display preference. -1 means no addon distance limit. */
public final class WallGunConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<Integer> MAX_RENDER_DISTANCE;
    public enum PreloadMode { LOADING, OFF }
    public static final ForgeConfigSpec.EnumValue<PreloadMode> PRELOAD_MODE;
    private static final ForgeConfigSpec.IntValue MODEL_CACHE_VERTICES, DIMENSION_CACHE_VERTICES;

    static {
        var builder = new ForgeConfigSpec.Builder();
        MAX_RENDER_DISTANCE = builder
                .comment("Maximum decorative-gun render distance in blocks (1-512). -1 disables the addon's distance limit; Minecraft still needs the chunk loaded.")
                .define("maxRenderDistance", 512, value -> value instanceof Integer distance && (distance == -1 || distance >= 1 && distance <= 512));
        PRELOAD_MODE=builder.comment("LOADING prepares the finite initial server scene inside the vanilla terrain screen before server activation. OFF explicitly skips preparation and allows first-view stutters. Both require the matching server protocol.").defineEnum("preloadMode",PreloadMode.LOADING);
        MODEL_CACHE_VERTICES=builder.comment("Vertex budget for reusable model memory. Currently displayed models remain pinned even if they exceed this budget. Resource reload invalidates the cache.").defineInRange("modelCacheVertexLimit",4_000_000,100_000,32_000_000);
        DIMENSION_CACHE_VERTICES=builder.comment("Maximum retained inactive-dimension GPU vertices; at most two inactive dimensions are retained. 0 disables dimension GPU retention. Logout/resource reload releases these buffers.").defineInRange("dimensionCacheVertexLimit",2_000_000,0,16_000_000);
        SPEC = builder.build();
    }

    public static boolean preloading() {return PRELOAD_MODE.get()==PreloadMode.LOADING;}
    public static int modelCacheVertices() {return MODEL_CACHE_VERTICES.get();}
    public static int dimensionCacheVertices() {return DIMENSION_CACHE_VERTICES.get();}
    private WallGunConfig() {}

    public static int maxRenderDistance() { return MAX_RENDER_DISTANCE.get(); }
}
