package dev.kiyo.wallgun;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only display preference. -1 means no addon distance limit. */
public final class WallGunConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<Integer> MAX_RENDER_DISTANCE;
    public static final ModConfigSpec.BooleanValue SHOW_PLACEMENT_HINT_ON_JOIN;
    public static final ModConfigSpec.BooleanValue CACHE_DECORATIVE_GUNS;

    static {
        var builder = new ModConfigSpec.Builder();
        CACHE_DECORATIVE_GUNS=builder.translation("config.tacz_wall_display.cacheDecorativeGuns")
                .comment("缓存装饰枪：开启时跨维度保留模型和 GPU 缓存，直到退出连接或资源重载。关闭时清理一次已有缓存，此后切换维度时释放；当前场景仍使用必要的临时渲染缓存。")
                .define("cacheDecorativeGuns",true);
        SHOW_PLACEMENT_HINT_ON_JOIN=builder.translation("config.tacz_wall_display.showPlacementHintOnJoin")
                .comment("进入存档或服务器后显示一次摆放操作提示；重生或切换维度不重复提示。").define("showPlacementHintOnJoin",true);
        MAX_RENDER_DISTANCE = builder
                .translation("config.tacz_wall_display.maxRenderDistance")
                .comment("装饰枪最大渲染距离，单位：方块。可设为 1–512；-1 表示不额外限制距离，但仍要求对应区块已加载。")
                .define("maxRenderDistance", 512, value -> value instanceof Integer distance && (distance == -1 || distance >= 1 && distance <= 512));
        SPEC = builder.build();
    }

    private WallGunConfig() {}

    public static int maxRenderDistance() { return MAX_RENDER_DISTANCE.get(); }
    public static boolean cacheDecorativeGuns() { return CACHE_DECORATIVE_GUNS.get(); }
}
