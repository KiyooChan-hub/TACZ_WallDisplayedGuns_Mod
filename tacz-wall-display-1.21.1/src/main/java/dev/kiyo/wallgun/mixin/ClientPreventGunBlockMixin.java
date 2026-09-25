package dev.kiyo.wallgun.mixin;

import com.tacz.guns.event.PreventGunClick;
import dev.kiyo.wallgun.client.PlacementClient;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PreventGunClick.class, remap = false)
public abstract class ClientPreventGunBlockMixin {
    @Inject(method = "onLeftClickBlock", at = @At("HEAD"), cancellable = true)
    private static void wallgun$allowClientBlockAttack(PlayerInteractEvent.LeftClickBlock event, CallbackInfo ci) {
        if (event.getLevel().isClientSide() && PlacementClient.interceptGun()) ci.cancel();
    }
}
