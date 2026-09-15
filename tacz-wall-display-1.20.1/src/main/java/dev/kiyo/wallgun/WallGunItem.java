package dev.kiyo.wallgun;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

public final class WallGunItem extends BlockItem {
    public WallGunItem(Block block, Properties properties) { super(block, properties); }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return dev.kiyo.wallgun.client.WallGunIcon.INSTANCE;}
        });
    }
    @Override public Component getName(ItemStack stack) {
        var snapshot = WallGuns.snapshot(stack);
        return snapshot == null ? super.getName(stack) : Component.translatable("item.tacz_wall_display.named", snapshot.copyGun().getHoverName());
    }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, java.util.List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable("tooltip.tacz_wall_display.place"));
        tooltip.add(Component.translatable("tooltip.tacz_wall_display.adjust", WallGunConfig.adjustmentItemName()));
        tooltip.add(Component.translatable("tooltip.tacz_wall_display.restore"));
    }
}
