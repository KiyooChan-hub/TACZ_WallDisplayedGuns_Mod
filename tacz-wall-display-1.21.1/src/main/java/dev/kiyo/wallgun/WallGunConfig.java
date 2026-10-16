package dev.kiyo.wallgun;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only display preference. -1 means no addon distance limit. */
public final class WallGunConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<Integer> MAX_RENDER_DISTANCE;
    public static final ModConfigSpec.BooleanValue SHOW_PLACEMENT_HINT_ON_JOIN;

    static {
        var builder = new ModConfigSpec.Builder();
        SHOW_PLACEMENT_HINT_ON_JOIN=builder.comment("Show placement controls once after joining a world or server. Does not repeat on respawn or dimension changes.").define("showPlacementHintOnJoin",true);
        MAX_RENDER_DISTANCE = builder
                .comment("Maximum decorative-gun render distance in blocks (1-512). -1 disables the addon's distance limit; Minecraft still needs the chunk loaded.")
                .define("maxRenderDistance", 512, value -> value instanceof Integer distance && (distance == -1 || distance >= 1 && distance <= 512));
        SPEC = builder.build();
    }

    private WallGunConfig() {}

    public static int maxRenderDistance() { return MAX_RENDER_DISTANCE.get(); }
}
