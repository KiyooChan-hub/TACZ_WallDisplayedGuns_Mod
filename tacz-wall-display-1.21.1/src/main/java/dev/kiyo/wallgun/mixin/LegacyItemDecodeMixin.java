package dev.kiyo.wallgun.mixin;

import dev.kiyo.wallgun.LegacyGunMigration;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla player/container NBT loading reaches this path before gameplay resumes. */
@Mixin(ItemStack.class)
public abstract class LegacyItemDecodeMixin {
    @Inject(method = "parseOptional", at = @At("RETURN"), cancellable = true)
    private static void wallgun$restoreLegacy(HolderLookup.Provider lookup, CompoundTag tag, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack original = cir.getReturnValue();
        if (original != null && !original.isEmpty()) cir.setReturnValue(LegacyGunMigration.restore(original));
    }
}
