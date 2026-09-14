package dev.kiyo.wallgun;

import com.tacz.guns.api.TimelessAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

public final class WallGunItem extends BlockItem {
    public WallGunItem(Block block, Properties properties) { super(block, properties); }
    @Override public Component getName(ItemStack stack) {
        var id = WallGuns.gunId(stack);
        return Component.translatable("item.tacz_wall_display.named", TimelessAPI.getCommonGunIndex(id)
                .map(index -> Component.translatable(index.getPojo().getName())).orElseGet(() -> Component.literal(id.toString())));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable("tooltip.tacz_wall_display.place"));
        tooltip.add(Component.literal(WallGuns.gunId(stack).toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
