package dev.kiyo.wallgun.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PlacementClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> TOGGLE_KEY;
    static {
        var builder = new ModConfigSpec.Builder();
        TOGGLE_KEY = builder.comment("Default free-placement key, e.g. key.keyboard.p. Minecraft Controls can override it per player.")
                .define("toggleKey", "key.keyboard.p");
        SPEC = builder.build();
    }
    private PlacementClientConfig() {}
}
