package dev.kiyo.wallgun.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent a cold kept-item frame from displaying the uninitialized arm pose. */
@Mixin(value = AnimateGeoItemRenderer.class, remap = false)
public abstract class FirstPersonInitializationMixin {
    @Shadow public abstract boolean needReInit(ItemStack stack);
    @Shadow public abstract void tryInit(ItemStack stack, Player player, float partialTick);

    @Inject(method = "renderFirstPerson", at = @At("HEAD"), cancellable = true)
    private void wallgun$initializeBeforeFirstFrame(LocalPlayer player, ItemStack stack,
            ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
            int light, float partialTick, CallbackInfo ci) {
        if (!needReInit(stack)) return;
        // TACZ retains the previous rendered stack during an equip transition.
        // Do not initialize its animation with the new main-hand item's context.
        if (!ItemStack.matches(player.getMainHandItem(), stack)) {
            ci.cancel();
            return;
        }
        tryInit(stack, player, partialTick);
    }
}
