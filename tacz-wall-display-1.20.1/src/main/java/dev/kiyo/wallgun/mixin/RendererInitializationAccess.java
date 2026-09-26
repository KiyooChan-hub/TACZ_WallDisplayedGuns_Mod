package dev.kiyo.wallgun.mixin;

import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access the inherited TACZ initialization API without modifying its lifecycle. */
@Mixin(value = AnimateGeoItemRenderer.class, remap = false)
public interface RendererInitializationAccess {
    @Invoker("needReInit") boolean wallgun$needsInitialization(ItemStack stack);
    @Invoker("tryInit") void wallgun$initialize(ItemStack stack, Player player, float partialTick);
}
