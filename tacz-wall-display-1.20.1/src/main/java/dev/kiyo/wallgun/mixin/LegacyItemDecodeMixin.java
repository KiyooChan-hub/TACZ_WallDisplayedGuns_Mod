package dev.kiyo.wallgun.mixin;

import dev.kiyo.wallgun.LegacyGunMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decode-time restoration catches player and container stacks before play begins. */
@Mixin(ItemStack.class)
public abstract class LegacyItemDecodeMixin {
    @Inject(method="of(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/ItemStack;",at=@At("RETURN"),cancellable=true)
    private static void wallgun$restoreLegacy(CompoundTag tag, CallbackInfoReturnable<ItemStack> cir){
        ItemStack original=cir.getReturnValue();
        if(original!=null && !original.isEmpty())cir.setReturnValue(LegacyGunMigration.restore(original));
    }
}
