package dev.kiyo.wallgun.mixin;

import com.tacz.guns.client.event.ClientPreventGunClick;
import dev.kiyo.wallgun.client.PlacementClient;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Let vanilla initiate block breaking with a gun while placement mode is active. */
@Mixin(value = ClientPreventGunClick.class, remap = false)
public abstract class ClientPreventGunClickMixin {
    @Inject(method = "onClickInput", at = @At("HEAD"), cancellable = true)
    private static void wallgun$allowBlockAttack(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo ci) {
        if (event.isAttack() && PlacementClient.allowGunBlockAttack()) ci.cancel();
    }
}
