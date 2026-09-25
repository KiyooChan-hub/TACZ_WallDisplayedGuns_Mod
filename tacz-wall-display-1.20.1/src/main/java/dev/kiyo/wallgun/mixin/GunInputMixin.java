package dev.kiyo.wallgun.mixin;

import dev.kiyo.wallgun.client.PlacementClient;
import com.tacz.guns.util.InputExtraCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TACZ's shared game-input gate covers fire, aim, reload, inspect, melee, etc. */
@Mixin(value = InputExtraCheck.class, remap = false)
public abstract class GunInputMixin {
    @Inject(method = "isInGame", at = @At("HEAD"), cancellable = true)
    private static void wallgun$disableGunInputs(CallbackInfoReturnable<Boolean> cir) {
        if (PlacementClient.interceptGun()) cir.setReturnValue(false);
    }
}
