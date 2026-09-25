package dev.kiyo.wallgun.mixin;

import com.tacz.guns.client.input.ReloadKey;
import dev.kiyo.wallgun.client.PlacementClient;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReloadKey.class, remap = false)
public abstract class AutoReloadMixin {
    @Inject(method = "autoReload", at = @At("HEAD"), cancellable = true)
    private static void wallgun$disableAutoReload(PlayerTickEvent event, CallbackInfo ci) {
        if (PlacementClient.interceptGun()) ci.cancel();
    }
}
