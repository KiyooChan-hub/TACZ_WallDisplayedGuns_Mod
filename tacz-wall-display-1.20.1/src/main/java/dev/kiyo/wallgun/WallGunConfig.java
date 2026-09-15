package dev.kiyo.wallgun;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

public final class WallGunConfig {
    public static final String FILE_NAME = "tacz-wall-display-server.toml";
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> ADJUSTMENT_ITEM;
    static {
        var builder = new ForgeConfigSpec.Builder();
        ADJUSTMENT_ITEM = builder.comment("Item ID used to rotate and flip decorative guns. Default: minecraft:stick.",
                "The item's own right-click behavior is not adapted. An unknown item ID matches no item.")
                .define("adjustmentItem", "minecraft:stick", value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        SPEC = builder.build();
    }
    private WallGunConfig() {}
    public static ResourceLocation adjustmentItemId() {
        // A server config is not yet loaded while viewing items outside a world.
        return new ResourceLocation(SPEC.isLoaded() ? ADJUSTMENT_ITEM.get() : "minecraft:stick");
    }
    public static boolean isAdjustmentItem(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(adjustmentItemId());
    }
    public static Component adjustmentItemName() {
        var id = adjustmentItemId();
        return BuiltInRegistries.ITEM.containsKey(id)
                ? Component.translatable(BuiltInRegistries.ITEM.get(id).getDescriptionId()) : Component.literal(id.toString());
    }
}
